# ingestion/signal_collector.py
# Main ingestion loop — runs continuously and triggers the signal fusion agent.
# Run as: python -m ingestion.signal_collector

import asyncio
import logging
import os
import uuid
from datetime import datetime

from dotenv import load_dotenv
from google.genai import types

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

# Monitored zones in Islamabad
MONITORED_ZONES = [
    {
        "area_name": "G-10",
        "lat": 33.6844,
        "lng": 73.0479,
        "search_query": "flood water rain G-10 Islamabad emergency",
    },
    {
        "area_name": "G-11",
        "lat": 33.6956,
        "lng": 73.0286,
        "search_query": "flood water rain G-11 Islamabad emergency",
    },
    {
        "area_name": "I-10",
        "lat": 33.6650,
        "lng": 73.0850,
        "search_query": "heat emergency hospital I-10 Islamabad",
    },
    {
        "area_name": "F-10",
        "lat": 33.7100,
        "lng": 73.0200,
        "search_query": "accident fire infrastructure F-10 Islamabad",
    },
]

POLL_INTERVAL_SECONDS = 120  # Poll every 2 minutes


async def run_collection_cycle():
    """Run one full collection cycle across all monitored zones."""
    from agents.signal_fusion.agent import signal_fusion_agent
    from google.adk.runners import Runner
    from google.adk.sessions import InMemorySessionService

    session_service = InMemorySessionService()
    runner = Runner(
        agent=signal_fusion_agent,
        app_name="ciro_ingestion",
        session_service=session_service,
    )

    cycle_id = str(uuid.uuid4())[:8]
    logger.info(f"=== Collection cycle {cycle_id} started ===")

    for zone in MONITORED_ZONES:
        logger.info(f"Collecting signals for {zone['area_name']}...")
        try:
            # Create a new session per zone per cycle
            session = await session_service.create_session(
                app_name="ciro_ingestion",
                user_id="collector",
            )

            # Build the message for the agent
            message_text = (
                f"Collect signals for this location:\n"
                f"- lat: {zone['lat']}\n"
                f"- lng: {zone['lng']}\n"
                f"- area_name: {zone['area_name']}\n"
                f"- search_query: {zone['search_query']}\n"
                f"- trigger_reason: scheduled_poll\n"
                f"\nPlease call all four tools (weather, traffic, social, gdacs) "
                f"and provide a summary of the collected signals."
            )

            content = types.Content(
                role="user",
                parts=[types.Part(text=message_text)],
            )

            # Run agent — yields events
            final_response = None
            async for event in runner.run_async(
                user_id="collector",
                session_id=session.id,
                new_message=content,
            ):
                if event.is_final_response() and event.content:
                    final_response = event.content.parts[0].text if event.content.parts else ""

            if final_response:
                logger.info(
                    f"  ✓ {zone['area_name']}: "
                    f"{final_response[:200]}..."
                )
            else:
                logger.warning(f"  ⚠ {zone['area_name']}: No final response from agent")

        except Exception as e:
            logger.error(f"  ✗ {zone['area_name']} failed: {e}", exc_info=True)
            continue

    logger.info(f"=== Collection cycle {cycle_id} complete ===")


async def main():
    """Main entry point — runs the collection loop indefinitely."""
    logger.info("=" * 60)
    logger.info("CIRO Signal Collector starting...")
    logger.info(f"Monitoring {len(MONITORED_ZONES)} zones")
    logger.info(f"Poll interval: {POLL_INTERVAL_SECONDS}s")
    logger.info("=" * 60)

    while True:
        try:
            await run_collection_cycle()
        except Exception as e:
            logger.error(f"Collection cycle failed: {e}", exc_info=True)

        logger.info(f"Sleeping {POLL_INTERVAL_SECONDS}s until next cycle...")
        await asyncio.sleep(POLL_INTERVAL_SECONDS)


if __name__ == "__main__":
    asyncio.run(main())
