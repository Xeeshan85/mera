# ingestion/mock_social_stream.py
# NOT a replacement for real data. Used only for:
# 1. Fallback when Apify is rate-limited
# 2. Demo scenario injector

import asyncio
import random
import uuid
from datetime import datetime, timedelta

from schemas.signal import Signal, SignalLocation

MOCK_SCENARIOS = {
    "flood_g10": [
        # (delay_seconds, text, urgency, lat_offset, lng_offset, followers)
        (0,   "Heavy rain in G-10 sector, roads getting slippery #Islamabad", 0.3, 0.001, -0.002, 850),
        (30,  "Water logging on Margalla Road G-10, avoid if possible", 0.5, 0.002, 0.001, 2300),
        (60,  "G-10 Margalla road completely flooded! Cars stuck. @islamabadpolice", 0.8, -0.001, 0.002, 12000),
        (90,  "HELP needed G-10/3 house flooding water entering, calling 1122", 0.95, 0.003, -0.001, 340),
        (120, "Rescue teams needed G-10 sector multiple families stranded #NDMA", 0.9, -0.002, 0.003, 45000),
        (150, "My neighbor says pipe burst on main G-10 road, not flooding?", 0.3, 0.001, 0.001, 280),
        (180, "UPDATE: Both flooding AND pipe burst confirmed G-10 @CDA_Official", 0.85, 0.0, 0.0, 98000),
    ],
    "heat_i10": [
        (0,   "Extreme heat in I-10 today, multiple people feeling unwell", 0.5, 0.001, 0.001, 1200),
        (60,  "3 elderly people collapsed due to heat in I-10 market area", 0.85, -0.001, 0.002, 8900),
        (120, "PIMS hospital reporting increased heat stroke patients from I-10", 0.9, 0.0, 0.0, 125000),
    ],
}

# Base coordinates for known areas
BASE_COORDS = {
    "G-10 Islamabad": (33.6844, 73.0479),
    "G-10": (33.6844, 73.0479),
    "G-11 Islamabad": (33.6956, 73.0286),
    "G-11": (33.6956, 73.0286),
    "I-10 Islamabad": (33.6650, 73.0850),
    "I-10": (33.6650, 73.0850),
    "F-10 Islamabad": (33.7100, 73.0200),
    "F-10": (33.7100, 73.0200),
}


def generate_signals(
    search_query: str,
    location_name: str,
    count: int = 10,
    scenario_key: str = None,
) -> list[Signal]:
    """
    Generate mock social signals as fallback when Apify is unavailable.

    Args:
        search_query: The search query that triggered this fallback.
        location_name: Human-readable location name.
        count: Maximum number of signals to generate.
        scenario_key: Specific scenario to use. Auto-detected from query if None.

    Returns:
        List of mock Signal objects marked with degraded_mode=True.
    """
    # Determine which scenario based on keywords
    if scenario_key is None:
        if any(w in search_query.lower() for w in ["flood", "water", "rain"]):
            scenario_key = "flood_g10"
        elif any(w in search_query.lower() for w in ["heat", "temperature"]):
            scenario_key = "heat_i10"
        else:
            scenario_key = "flood_g10"

    base_lat, base_lng = BASE_COORDS.get(location_name, (33.6844, 73.0479))

    signals = []
    scenario = MOCK_SCENARIOS.get(scenario_key, MOCK_SCENARIOS["flood_g10"])

    for i, (delay, text, urgency, dlat, dlng, followers) in enumerate(scenario[:count]):
        signals.append(Signal(
            source_type="social",
            source_name="mock_social",
            timestamp=datetime.utcnow() - timedelta(seconds=random.randint(0, 600)),
            location=SignalLocation(
                lat=base_lat + dlat,
                lng=base_lng + dlng,
                area_name=location_name,
                geolocation_confidence=0.65,
                radius_m=500,
            ),
            raw_payload={
                "text": text,
                "followers_count": followers,
                "retweet_count": random.randint(0, 50),
                "like_count": random.randint(0, 200),
                "is_mock": True,
            },
            credibility_score=min(0.25 + (followers / 200000), 0.65),
            urgency_language_score=urgency,
            mention_velocity=len(scenario),
            degraded_mode=True,  # Always mark mock data as degraded
        ))

    return signals


async def inject_demo_scenario(scenario_key: str, location_name: str):
    """
    Inject a demo scenario with realistic timing for live demos.
    Writes directly to Firestore + publishes to Pub/Sub.
    Call this from a demo runner script.

    Args:
        scenario_key: Key from MOCK_SCENARIOS (e.g., "flood_g10").
        location_name: Human-readable location name.
    """
    from services.firestore_service import FirestoreService
    from services.pubsub_service import PubSubService

    firestore = FirestoreService()
    pubsub = PubSubService()
    scenario = MOCK_SCENARIOS.get(scenario_key, [])

    base_lat, base_lng = BASE_COORDS.get(location_name, (33.6844, 73.0479))

    print(f"[DEMO] Starting scenario '{scenario_key}' for {location_name}")
    print(f"[DEMO] {len(scenario)} events will be injected")

    for delay, text, urgency, dlat, dlng, followers in scenario:
        await asyncio.sleep(delay if delay == 0 else 15)  # Accelerated for demo

        signal = Signal(
            source_type="social",
            source_name="mock_social",
            timestamp=datetime.utcnow(),
            location=SignalLocation(
                lat=base_lat + dlat,
                lng=base_lng + dlng,
                area_name=location_name,
                geolocation_confidence=0.65,
            ),
            raw_payload={
                "text": text,
                "followers_count": followers,
                "is_mock": True,
            },
            credibility_score=min(0.25 + (followers / 200000), 0.65),
            urgency_language_score=urgency,
            mention_velocity=len(scenario),
            degraded_mode=True,
        )

        await firestore.write_signal(signal)
        await pubsub.publish_signal(signal)
        print(f"  [DEMO] Injected: {text[:60]}...")

    print(f"[DEMO] Scenario '{scenario_key}' complete!")
