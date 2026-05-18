#!/usr/bin/env python3
"""Step through a match using phase-tick (gateway timer simulation)."""

from __future__ import annotations

import argparse
import os
import sys
import time

import requests

BASE = os.environ.get("WERWOLF_BASE_URL", "http://localhost:8080").rstrip("/")
API = f"{BASE}/internal/game"
MAX_TICKS = int(os.environ.get("WERWOLF_MAX_TICKS", "10000"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--room-id", help="existing room; if omitted, creates one")
    args = parser.parse_args()

    if args.room_id:
        room_id = args.room_id
    else:
        room = requests.post(f"{API}/rooms", json={}, timeout=30).json()
        room_id = room["roomId"]
        start = requests.post(f"{API}/rooms/{room_id}/start", timeout=30).json()
        if not start.get("success", True):
            print("start failed:", start, file=sys.stderr)
            return 1
        print(f"created roomId={room_id}")

    for i in range(MAX_TICKS):
        tick = requests.post(f"{API}/rooms/{room_id}/phase-tick", timeout=30).json()
        status = tick.get("status")
        if i % 50 == 0 or status in ("GAME_OVER", "STUCK"):
            print(f"tick {i}: {tick}")
        if status == "GAME_OVER":
            snap = requests.get(f"{API}/rooms/{room_id}", timeout=30).json()
            print("final:", snap.get("phase"), snap.get("status"))
            return 0
        if status == "STUCK":
            return 2
        time.sleep(0)

    print("max ticks exceeded", file=sys.stderr)
    return 3


if __name__ == "__main__":
    raise SystemExit(main())
