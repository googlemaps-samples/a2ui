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
import os

from a2a.server.apps import A2AStarletteApplication
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.tasks import InMemoryTaskStore
import click
import dotenv
from starlette.middleware.cors import CORSMiddleware
from starlette.responses import RedirectResponse
from starlette.staticfiles import StaticFiles
import uvicorn

from python_agent.agent import MAUIAgent
from python_agent.agent_config import AgentConfig, FallbackMode
from python_agent.agent_with_grounding import MAUIAgentWithGrounding
from python_agent.agent_with_templates import MAUIAgentWithTemplates
from agent_executor import MAUIAgentExecutor

dotenv.load_dotenv()

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class MissingAPIKeyError(Exception):
  """Exception for missing API key."""


@click.command()
@click.option("--serverurl", default="")
@click.option("--host", default="0.0.0.0")
@click.option("--port", default=10002)
@click.option(
    "--agent",
    default="MAUIAgent",
    show_default=True,
    envvar="A2UI_DEFAULT_AGENT",
    help=(
        "Agent to use as default. Accepts class name (e.g., 'MAUIAgent',"
        " 'MAUIAgentWithTemplates', 'MAUIAgentWithGrounding') or shorthand"
        " ('BASE', 'TEMPLATE', 'GROUNDING')."
    ),
)
def main(serverurl, host, port, agent):
  try:
    # Check for API key only if Vertex AI is not configured
    if not os.getenv("GOOGLE_GENAI_USE_VERTEXAI") == "TRUE":
      if not os.getenv("GEMINI_API_KEY"):
        raise MissingAPIKeyError(
            "GEMINI_API_KEY environment variable not set and"
            " GOOGLE_GENAI_USE_VERTEXAI is not TRUE."
        )

    base_url = f"http://{host}:{port}"

    if serverurl != "":
      base_url = serverurl

    fallback_mode_env = os.getenv("A2UI_FALLBACK_MODE")
    if fallback_mode_env:
      config = AgentConfig(fallback_mode=FallbackMode(fallback_mode_env))
    else:
      config = AgentConfig()
    logger.info(f"Using fallback_mode: {config.fallback_mode}")

    ui_agent = MAUIAgent(base_url=base_url)
    grounding_agent = MAUIAgentWithGrounding(base_url=base_url)
    template_agent = MAUIAgentWithTemplates(base_url=base_url, config=config)

    agent_map = {
        "MAUIAGENT": ui_agent,
        "BASE": ui_agent,
        "MAUIAGENTWITHGROUNDING": grounding_agent,
        "GROUNDING": grounding_agent,
        "MAUIAGENTWITHTEMPLATES": template_agent,
        "TEMPLATE": template_agent,
    }

    normalized_agent = agent.upper()
    if normalized_agent not in agent_map:
      raise ValueError(
          f"Unknown agent: {agent}. Expected one of {list(agent_map.keys())}"
      )

    default_agent = agent_map[normalized_agent]
    logger.info(
        f"--- SERVER: Binding {default_agent.__class__.__name__} as default"
        " agent ---"
    )

    agent_executor = MAUIAgentExecutor(
        default_agent=default_agent,
        grounding_agent=grounding_agent,
        template_agent=template_agent,
    )

    request_handler = DefaultRequestHandler(
        agent_executor=agent_executor,
        task_store=InMemoryTaskStore(),
    )
    server = A2AStarletteApplication(
        agent_card=default_agent.agent_card, http_handler=request_handler
    )

    app = server.build()

    app.add_middleware(
        CORSMiddleware,
        allow_origin_regex=r"http://localhost:\d+",
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    logger.info(f"Starting A2A server on {host}:{port}")
    uvicorn.run(app, host=host, port=port)
  except Exception as e:
    logger.error(f"An error occurred during server startup: {e}")
    exit(1)


if __name__ == "__main__":
  main()
