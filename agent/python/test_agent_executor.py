# Copyright 2026 Google LLC
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

"""Unit tests for MAUIAgentExecutor routing and execution."""

import unittest
from unittest import mock

from a2a.server.agent_execution import RequestContext
from a2a.server.events import EventQueue
from a2a.utils.errors import ServerError

from agent import MAUIAgent
from agent_with_grounding import MAUIAgentWithGrounding
from agent_with_templates import MAUIAgentWithTemplates
from agent_executor import MAUIAgentExecutor  # pylint: disable=g-importing-member


async def _empty_async_stream(*args, **kwargs):
  del args, kwargs
  yield {"is_task_complete": True, "parts": []}


def _create_mock_agent(cls):
  agent = mock.MagicMock(spec=cls)
  agent.agent_card = mock.MagicMock()
  agent.agent_card.extensions = []
  agent.stream = mock.MagicMock(side_effect=_empty_async_stream)
  return agent


class MAUIAgentExecutorTest(unittest.IsolatedAsyncioTestCase):
  """Unit tests for MAUIAgentExecutor routing."""

  def setUp(self):
    super().setUp()
    self.default_agent = _create_mock_agent(MAUIAgent)
    self.grounding_agent = _create_mock_agent(MAUIAgentWithGrounding)
    self.template_agent = _create_mock_agent(MAUIAgentWithTemplates)
    self.ui_agent = _create_mock_agent(MAUIAgent)
    self.grounding_template_agent = _create_mock_agent(MAUIAgentWithGrounding)

    self.executor = MAUIAgentExecutor(
        default_agent=self.default_agent,
        grounding_agent=self.grounding_agent,
        template_agent=self.template_agent,
        ui_agent=self.ui_agent,
        grounding_template_agent=self.grounding_template_agent,
    )

  def _build_context(self, user_input: str) -> RequestContext:
    context = mock.MagicMock(spec=RequestContext)
    context.message = None
    context.get_user_input.return_value = user_input
    context.requested_extensions = []
    task = mock.MagicMock()
    task.id = "task-1"
    task.context_id = "ctx-1"
    context.current_task = task
    return context

  async def test_route_mcp(self):
    """Verifies that [MCP] query routes to ui_agent and strips prefix."""
    context = self._build_context("[MCP] coffee")
    event_queue = mock.MagicMock(spec=EventQueue)
    await self.executor.execute(context, event_queue)
    self.ui_agent.stream.assert_called_once()
    self.assertEqual(self.ui_agent.stream.call_args[0][0], "coffee")

  async def test_route_grounding(self):
    """Verifies that [GROUNDING] query routes to grounding_agent and strips prefix."""
    context = self._build_context("[GROUNDING] coffee")
    event_queue = mock.MagicMock(spec=EventQueue)
    await self.executor.execute(context, event_queue)
    self.grounding_agent.stream.assert_called_once()
    self.assertEqual(self.grounding_agent.stream.call_args[0][0], "coffee")

  async def test_route_template(self):
    """Verifies that [TEMPLATE] query routes to template_agent and strips prefix."""
    context = self._build_context("[TEMPLATE] coffee")
    event_queue = mock.MagicMock(spec=EventQueue)
    await self.executor.execute(context, event_queue)
    self.template_agent.stream.assert_called_once()
    self.assertEqual(self.template_agent.stream.call_args[0][0], "coffee")

  async def test_route_grounding_template(self):
    """Verifies that [GROUNDING_TEMPLATE] query routes to grounding_template_agent and strips prefix."""
    context = self._build_context("[GROUNDING_TEMPLATE] coffee")
    event_queue = mock.MagicMock(spec=EventQueue)
    await self.executor.execute(context, event_queue)
    self.grounding_template_agent.stream.assert_called_once()
    self.assertEqual(
        self.grounding_template_agent.stream.call_args[0][0], "coffee"
    )

  async def test_route_unprefixed_query(self):
    """Verifies that unprefixed query routes to default_agent."""
    context = self._build_context("coffee")
    event_queue = mock.MagicMock(spec=EventQueue)
    await self.executor.execute(context, event_queue)
    self.default_agent.stream.assert_called_once()
    self.assertEqual(self.default_agent.stream.call_args[0][0], "coffee")

  async def test_missing_template_agent_raises(self):
    """Verifies that [TEMPLATE] query raises ServerError when template_agent is missing."""
    executor = MAUIAgentExecutor(
        default_agent=self.default_agent,
        grounding_agent=self.grounding_agent,
        template_agent=None,
        ui_agent=self.ui_agent,
        grounding_template_agent=self.grounding_template_agent,
    )
    context = self._build_context("[TEMPLATE] coffee")
    event_queue = mock.MagicMock(spec=EventQueue)
    with self.assertRaises(ServerError) as exc_info:
      await executor.execute(context, event_queue)
    self.assertEqual(
        exc_info.exception.error.message, "Template Agent is not configured."
    )

  async def test_missing_grounding_template_agent_raises(self):
    """Verifies that [GROUNDING_TEMPLATE] query raises ServerError when grounding_template_agent is missing."""
    executor = MAUIAgentExecutor(
        default_agent=self.default_agent,
        grounding_agent=self.grounding_agent,
        template_agent=self.template_agent,
        ui_agent=self.ui_agent,
        grounding_template_agent=None,
    )
    context = self._build_context("[GROUNDING_TEMPLATE] coffee")
    event_queue = mock.MagicMock(spec=EventQueue)
    with self.assertRaises(ServerError) as exc_info:
      await executor.execute(context, event_queue)
    self.assertEqual(
        exc_info.exception.error.message,
        "Grounding Template Agent is not configured.",
    )


if __name__ == "__main__":
  unittest.main()
