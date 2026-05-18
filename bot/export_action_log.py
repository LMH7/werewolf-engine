#!/usr/bin/env python3
"""Export in-memory action_log for a room (or run mock-auto-play then export)."""

from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

import requests

BASE = os.environ.get("WERWOLF_BASE_URL", "http://localhost:8080").rstrip("/")
API = f"{BASE}/internal/game"
OUT_DIR = Path(__file__).resolve().parent / "action_logs"


def fetch_snapshot(room_id: str) -> dict:
    return requests.get(f"{API}/rooms/{room_id}", timeout=30).json()


def fetch_action_log(room_id: str) -> list[dict]:
    data = requests.get(f"{API}/rooms/{room_id}/action-log", timeout=30).json()
    return data.get("entries", [])


def run_mock_game() -> str:
    room = requests.post(f"{API}/rooms", json={}, timeout=30).json()
    room_id = room["roomId"]
    start = requests.post(f"{API}/rooms/{room_id}/start", timeout=30).json()
    if not start.get("success", True):
        raise RuntimeError(f"start failed: {start}")
    result = requests.post(f"{API}/rooms/{room_id}/mock-auto-play", timeout=600).json()
    print(f"roomId={room_id} outcome={result.get('outcome')} winner={result.get('winner')} steps={result.get('steps')}")
    return room_id


def export_room(room_id: str, out_path: Path | None = None) -> Path:
    snap = fetch_snapshot(room_id)
    entries = fetch_action_log(room_id)
    payload = {
        "exportedAt": datetime.now(timezone.utc).isoformat(),
        "roomId": room_id,
        "snapshot": {
            "status": snap.get("status"),
            "phase": snap.get("phase"),
            "round": snap.get("round"),
            "alivePlayers": snap.get("alivePlayers"),
            "players": snap.get("players"),
        },
        "entryCount": len(entries),
        "entries": entries,
    }
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    path = out_path or (OUT_DIR / f"{room_id}.json")
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"exported {len(entries)} entries -> {path}")
    return path


def validate_entries(entries: list[dict]) -> list[str]:
    allowed: dict[str, set[str | None]] = {
        "NIGHT_WOLF": {"KILL", "WOLF_CHAT"},
        "NIGHT_SEER": {"CHECK", "SKIP"},
        "NIGHT_WITCH": {"SAVE", "POISON", "SKIP"},
        "DAY_DISCUSS": {"SPEAK", "SKIP_SPEAK"},
        "DAY_VOTE": {"VOTE", "SKIP_VOTE"},
        "HUNTER_SHOOT": {"SHOOT", "SKIP"},
        "LAST_WORDS": {"SPEAK", "SKIP_SPEAK"},
    }
    issues: list[str] = []
    for i, e in enumerate(entries, 1):
        if not e.get("playerId"):
            continue
        act = e.get("action")
        phase = e.get("phase")
        if act and phase and phase in allowed and act not in allowed[phase]:
            issues.append(f"#{i} P{e['playerId']} {act} in {phase}")
    return issues


def print_timeline(entries: list[dict]) -> None:
    last: tuple | None = None
    for i, e in enumerate(entries, 1):
        key = (e.get("round"), e.get("phase"))
        if key != last:
            print(f"\n--- Round {e.get('round')} | {e.get('phase')} ---")
            last = key
        pid = e.get("playerId") or "-"
        role = e.get("role") or "-"
        act = e.get("action") or "SYS"
        tgt = e.get("target")
        tgt_s = f" ->{tgt}" if tgt is not None else ""
        content = (e.get("content") or "")[:50]
        extra = f" | {content}" if content else ""
        print(f"{i:3} P{pid:>2} {str(role):12} {act:12}{tgt_s:5} OK{extra}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Export werewolf action_log JSON")
    parser.add_argument("--room-id", help="Existing room id (skip mock-auto-play)")
    parser.add_argument("--play", action="store_true", help="Run mock-auto-play first")
    parser.add_argument("--print", action="store_true", dest="print_timeline", help="Print timeline to stdout")
    parser.add_argument("--validate", action="store_true", help="Check phase/action consistency")
    parser.add_argument("-o", "--output", type=Path, help="Output JSON path")
    args = parser.parse_args()

    room_id = args.room_id
    if args.play or not room_id:
        room_id = run_mock_game()
    if not room_id:
        parser.error("room-id required unless --play")

    path = export_room(room_id, args.output)
    data = json.loads(path.read_text(encoding="utf-8"))
    if args.validate:
        issues = validate_entries(data["entries"])
        if issues:
            print("validation issues:", *issues[:20], sep="\n  ")
            if len(issues) > 20:
                print(f"  ... and {len(issues) - 20} more")
            return 1
        print("validation OK")
    if args.print_timeline:
        print_timeline(data["entries"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
