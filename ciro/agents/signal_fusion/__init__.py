# agents/signal_fusion/__init__.py
# ADK-compatible package init — exports `agent` for `adk web` command.

from agents.signal_fusion.agent import signal_fusion_agent

agent = signal_fusion_agent
