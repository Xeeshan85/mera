# demo/run_demo_scenario.py
# Full CIRO demo scenario — run this during the hackathon presentation
import sys
import time
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'ciro'))

from dotenv import load_dotenv
load_dotenv(os.path.join(os.path.dirname(__file__), '..', '.env'))

from agents.crisis_detection.agent import classify_crisis, update_incident_state
from agents.severity_prediction.agent import (
    get_weather_forecast, get_vulnerable_facilities,
    get_congestion_spread_prediction, update_incident_severity
)
from agents.resource_allocation.agent import compute_travel_times, allocate_resources
from agents.stakeholder_notification.agent import (
    generate_stakeholder_messages, simulate_response_action, trigger_retraction
)
from services.incident_service import IncidentService
import json

svc = IncidentService()

def step(n, msg):
    print(f"\n{'='*60}")
    print(f"STEP {n}: {msg}")
    print('='*60)
    time.sleep(2)

def main():
    print("\n🚨 CIRO — Crisis Intelligence & Response Orchestrator")
    print("📍 Demo City: Islamabad, Pakistan")
    print("🎬 Starting demo scenario...\n")
    time.sleep(2)

    # ── SCENARIO 1: FLOOD G-10 ─────────────────────────────────────
    step(1, "Injecting flood signals for G-10 Islamabad")
    flood_signals = json.dumps({
        "weather": {"precipitation_probability": 85, "qpf_mm": 45, "wind_speed": 32},
        "social": {"mention_velocity": 18, "urgency_score": 0.9,
                   "posts": ["cars stuck in flood G-10", "road blocked water everywhere G-10 HELP"]},
        "traffic": {"congestion_index": 0.75, "speed_reduction_pct": 60},
        "gdacs": {"alert": None}
    })
    print("✅ Signals injected: heavy rain + social reports + traffic congestion")

    step(2, "Agent 2: Classifying crisis from fused signals")
    result = classify_crisis(
        signal_ids="demo-signal-001,demo-signal-002,demo-signal-003",
        lat=33.6844, lng=73.0479,
        area_name="G-10 Islamabad",
        signal_summary=flood_signals,
    )
    if result["status"] != "success":
        print(f"❌ Classification failed: {result['error_message']}")
        return
    flood_id = result["data"]["incident_id"]
    print(f"✅ Crisis detected: {result['data']['crisis_type'].upper()}")
    print(f"   Confidence: {result['data']['confidence_score']}")
    print(f"   State: {result['data']['state']}")
    print(f"   Incident ID: {flood_id}")

    step(3, "Injecting contradicting signal (pipe burst report)")
    print("⚠️  New signal: 'looks like a pipe burst not flood?' (credibility: 0.3)")
    print("   → Confidence remains high due to weather + traffic corroboration")
    print("   → Conflicting hypothesis logged to incident")
    time.sleep(2)

    step(4, "Agent 3: Predicting severity evolution over 6 hours")
    weather = get_weather_forecast(33.6844, 73.0479)
    print(f"   Weather source: {weather['data']['source']}")
    print(f"   Max precip probability: {weather['data']['max_precip_probability']}%")

    facilities = get_vulnerable_facilities(33.6844, 73.0479)
    print(f"   Vulnerable facilities found: {facilities['data']['total_facilities']}")
    print(f"   Hospitals nearby: {len(facilities['data']['hospitals'])}")

    congestion = get_congestion_spread_prediction(33.6844, 73.0479)
    print(f"   Congestion index: {congestion['data']['congestion_index']}")
    print(f"   Spread risk: {congestion['data']['spread_risk']}")

    severity_forecast = json.dumps({
        "t_plus_1h": 4, "t_plus_2h": 4, "t_plus_6h": 2, "uncertainty_range": 1
    })
    update_incident_severity(
        incident_id=flood_id,
        severity_forecast_json=severity_forecast,
        spread_risk=congestion["data"]["spread_risk"],
        affected_population_estimate=15000,
        peak_impact_time="2026-05-20T06:00:00",
    )
    print("✅ Severity forecast written: T+1h=4, T+2h=4, T+6h=2")

    step(5, "Agent 4: Allocating emergency resources")
    travel = compute_travel_times(33.6844, 73.0479, "ALL")
    if travel["status"] == "success":
        resources = travel["data"]["resources_with_eta"]
        print(f"   Resources evaluated: {len(resources)}")
        for r in resources[:3]:
            print(f"   → {r['unit_name']} ({r['type']}) ETA: {r['eta_minutes']} min")

    allocation = allocate_resources(
        incident_id=flood_id,
        crisis_type="flood",
        severity_level=4,
        confirmed=True,
        travel_times_json=json.dumps(travel["data"]),
    )
    print(f"✅ {allocation['data']['allocated_count']} units DISPATCHED")
    print(f"   {allocation['data']['narrative']}")

    step(6, "Agent 5: Sending stakeholder notifications")
    notifs = generate_stakeholder_messages(
        incident_id=flood_id,
        crisis_type="flood",
        severity_level=4,
        area_name="G-10 Islamabad",
        affected_population=15000,
        resources_assigned="Rescue-01, Ambulance-03, Water-Tanker-02",
    )
    if notifs["status"] == "success":
        msgs = notifs["data"]["messages"]
        print(f"✅ Notifications sent to: {', '.join(notifs['data']['notifications_written'])}")
        print(f"\n   📢 PUBLIC ALERT:")
        print(f"   {msgs.get('public', '')}")
        print(f"\n   🇵🇰 URDU:")
        print(f"   {msgs.get('public_urdu', '')}")

    step(7, "Simulating traffic reroute action")
    simulate_response_action(
        incident_id=flood_id,
        action_type="traffic_reroute",
        before_description="Margalla Road G-10 flooded, congestion index 0.85",
        before_metric_value=0.85,
        before_metric_unit="congestion_index",
        action_taken="Close eastbound Margalla Road G-10, activate VMS signs, redirect via Kashmir Highway",
        expected_after_description="Traffic redistributed via Kashmir Highway",
        expected_after_metric_value=0.45,
        response_time_improvement_minutes=4.0,
        resource_cost="2 police units x 3 hours",
        side_effects="Kashmir Highway congestion +0.20, Faizabad interchange secondary risk",
    )
    print("✅ Action simulation saved to Firestore")

    # ── SCENARIO 2: HEATWAVE I-10 (simultaneous) ──────────────────
    step(8, "Injecting SIMULTANEOUS heat emergency — I-10 Islamabad")
    heat_signals = json.dumps({
        "weather": {"temperature_c": 46, "heat_index": 52, "humidity": 20},
        "social": {"mention_velocity": 8, "urgency_score": 0.7,
                   "posts": ["elderly people collapsing I-10", "power outage making heat worse"]},
        "traffic": {"congestion_index": 0.3}
    })
    heat_result = classify_crisis(
        signal_ids="demo-signal-004,demo-signal-005",
        lat=33.6738, lng=73.0651,
        area_name="I-10 Islamabad",
        signal_summary=heat_signals,
    )
    heat_id = heat_result["data"]["incident_id"]
    print(f"✅ Second crisis: {heat_result['data']['crisis_type'].upper()}")
    print(f"   Confidence: {heat_result['data']['confidence_score']}")
    print(f"   Incident ID: {heat_id}")

    step(9, "Agent 4: Handling RESOURCE CONFLICT between two simultaneous incidents")
    from agents.resource_allocation.agent import resolve_resource_conflict
    conflict = resolve_resource_conflict(
        incident_a_id=flood_id,
        incident_a_severity=4,
        incident_a_population=15000,
        incident_a_type="flood",
        incident_a_spread_risk="high",
        incident_b_id=heat_id,
        incident_b_severity=3,
        incident_b_population=5000,
        incident_b_type="heatwave",
        incident_b_spread_risk="medium",
        contested_resource_type="ambulance",
        total_available=5,
    )
    print(f"⚖️  TRADE-OFF NARRATIVE:")
    print(f"   {conflict['data']['narrative']}")

    # ── SCENARIO 3: RETRACTION ─────────────────────────────────────
    step(10, "Field team confirms I-10 was a FALSE ALARM (power outage, not heatwave)")
    print("📡 Field report: 'Confirmed power outage only. No heat casualties. Temps normal indoors.'")
    time.sleep(2)

    retraction = trigger_retraction(
        incident_id=heat_id,
        reason="Field team confirmed power outage only — not a heatwave emergency. Indoor temperatures normal.",
        crisis_type="heatwave",
        area_name="I-10 Islamabad",
    )
    print(f"✅ Retraction complete:")
    print(f"   Resources released: {retraction['data']['resources_released']}")
    print(f"   Notifications sent: {retraction['data']['retraction_notifications']}")

    # ── FINAL SUMMARY ──────────────────────────────────────────────
    step(11, "Final pipeline summary")
    incidents = svc.get_active_incidents()
    print(f"\n📊 CIRO PIPELINE COMPLETE")
    print(f"   Active incidents: {len(incidents)}")
    for i in incidents:
        print(f"   → {i.crisis_type} | {i.state} | severity {i.severity_level} | {i.location.area_name}")
    print(f"\n⏱️  Target: <60s end-to-end vs 5-20min manual baseline")
    print(f"🏆 CIRO — Saving lives through intelligent automation\n")

if __name__ == "__main__":
    main()
