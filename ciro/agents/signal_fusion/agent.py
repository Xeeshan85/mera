# agents/signal_fusion/agent.py
# Signal Fusion Agent — ADK 2.0 Agent 1
# Collects signals from all sources, normalizes, scores, writes to Firestore + Pub/Sub.

import os
from dotenv import load_dotenv

from google.adk.agents import Agent
from google.genai import types

from agents.signal_fusion.tools.weather_tool import get_weather_signal
from agents.signal_fusion.tools.traffic_tool import get_traffic_signal
from agents.signal_fusion.tools.social_tool import get_social_signals
from agents.signal_fusion.tools.gdacs_tool import get_gdacs_signals

load_dotenv()

AGENT_INSTRUCTION = """\
You are the Signal Fusion Agent for CIRO — Crisis Intelligence & Response Orchestrator.
Your job is to collect signals from all available sources for a given location,
normalize them, score their credibility, detect contradictions, and produce a
fused signal set ready for crisis detection.

When called with a location (lat, lng, area_name, search_query):
1. Call get_weather_signal with the lat, lng, and area_name to get current conditions and forecast
2. Call get_traffic_signal with the lat, lng, and area_name to get congestion data
3. Call get_social_signals with the search_query and area_name to get citizen reports from Twitter/X
4. Call get_gdacs_signals with the lat and lng to get official disaster alerts
5. Return a structured summary including:
   - Total signals collected from all sources
   - Source breakdown (which sources succeeded, counts, degraded status)
   - Highest credibility score across all signals
   - Number of contradiction flags raised
   - Overall degraded mode status (true if any source used fallback)

Always collect from ALL sources even if one fails. Never abort early.
Report which sources succeeded and which fell back to alternatives.
Format your response as a clear summary with the signal data.
"""

signal_fusion_agent = Agent(
    name="signal_fusion_agent",
    model="gemini-2.5-flash",
    instruction=AGENT_INSTRUCTION,
    tools=[
        get_weather_signal,
        get_traffic_signal,
        get_social_signals,
        get_gdacs_signals,
    ],
    generate_content_config=types.GenerateContentConfig(
        temperature=0.1,  # Low temperature — this is analytical, not creative
    ),
)
