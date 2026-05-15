# Copyright 2025 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import logging

from a2a.server.agent_execution import AgentExecutor, RequestContext
from a2a.server.events import EventQueue
from a2a.server.tasks import TaskUpdater
from a2a.types import (
    DataPart,
    Part,
    Task,
    TaskState,
    TextPart,
    UnsupportedOperationError,
)
from a2a.utils import (
    new_agent_parts_message,
    new_agent_text_message,
    new_task,
)
from a2a.utils.errors import ServerError
from a2ui.a2a.extension import try_activate_a2ui_extension
from agent import MAUIAgent
from agent_with_grounding import MAUIAgentWithGrounding

logger = logging.getLogger(__name__)


class MAUIAgentExecutor(AgentExecutor):
  """MAUI AgentExecutor Example."""

  def __init__(self, default_agent: MAUIAgent, grounding_agent: MAUIAgentWithGrounding):
    self._default_agent = default_agent
    self._grounding_agent = grounding_agent

  async def execute(
      self,
      context: RequestContext,
      event_queue: EventQueue,
  ) -> None:
    query = ""
    ui_event_part = None
    action = None

    if context.message and context.message.parts:
      logger.info(
          f"--- AGENT_EXECUTOR: Processing {len(context.message.parts)} message"
          " parts ---"
      )
      for i, part in enumerate(context.message.parts):
        if isinstance(part.root, DataPart):
          if "userAction" in part.root.data:
            logger.info(f"  Part {i}: Found a2ui UI ClientEvent payload.")
            ui_event_part = part.root.data["userAction"]
          else:
            logger.info(f"  Part {i}: DataPart (data: {part.root.data})")
        elif isinstance(part.root, TextPart):
          logger.info(f"  Part {i}: TextPart (text: {part.root.text})")
        else:
          logger.info(f"  Part {i}: Unknown part type ({type(part.root)})")

    if ui_event_part:
      logger.info(f"Received a2ui ClientEvent: {ui_event_part}")
      action = ui_event_part.get("actionName")
      ctx = ui_event_part.get("context", {})
      
      # Use switch statement to route to the appropriate action handler
      query = f"User submitted an event: {action} with data: {ctx}"
      
    else:
      logger.info("No a2ui UI event part found. Falling back to text input.")
      query = context.get_user_input()

    # Interpret prefix and choose agent
    agent_to_use = self._default_agent
    if query.startswith("[GROUNDING]"):
        logger.info("--- AGENT_EXECUTOR: Prefix [GROUNDING] detected. Using Grounding Agent. ---")
        agent_to_use = self._grounding_agent
        query = query[len("[GROUNDING]"):].strip()
    else:
        logger.info("--- AGENT_EXECUTOR: No prefix detected. Using Default Agent. ---")

    logger.info(f"--- AGENT_EXECUTOR: Final query for LLM: '{query}' ---")

    logger.info(f"--- Client requested extensions: {context.requested_extensions} ---")
    active_ui_version = try_activate_a2ui_extension(context, agent_to_use.agent_card)

    # Determine which agent to use based on whether the a2ui extension is active.
    if active_ui_version:
      logger.info("--- AGENT_EXECUTOR: A2UI extension is active. Using UI agent. ---")
    else:
      logger.info(
          "--- AGENT_EXECUTOR: A2UI extension is not active. Using text agent. ---"
      )

    task = context.current_task

    if not task:
      task = new_task(context.message)
      await event_queue.enqueue_event(task)
    updater = TaskUpdater(event_queue, task.id, task.context_id)

    async for item in agent_to_use.stream(query, task.context_id, active_ui_version):
      is_task_complete = item["is_task_complete"]
      if not is_task_complete:
        message = None
        if "parts" in item:
          message = new_agent_parts_message(item["parts"], task.context_id, task.id)
        elif "updates" in item:
          message = new_agent_text_message(item["updates"], task.context_id, task.id)

        if message:
          await updater.update_status(TaskState.working, message)
        continue

      final_state = (
          TaskState.completed
          if action == "submit_booking"
          else TaskState.input_required
      )

      final_parts = item["parts"]

      logger.info("--- FINAL PARTS TO BE SENT ---")
      for i, part in enumerate(final_parts):
        logger.info(f"  - Part {i}: Type = {type(part.root)}")
        if isinstance(part.root, TextPart):
          logger.info(f"    - Text: {part.root.text[:200]}...")
        elif isinstance(part.root, DataPart):
          logger.info(f"    - Data: {str(part.root.data)[:200]}...")
      logger.info("-----------------------------")

      await updater.update_status(
          final_state,
          new_agent_parts_message(final_parts, task.context_id, task.id),
          final=(final_state == TaskState.completed),
      )
      break

  async def cancel(
      self, request: RequestContext, event_queue: EventQueue
  ) -> Task | None:
    raise ServerError(error=UnsupportedOperationError())
