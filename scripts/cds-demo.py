#!/usr/bin/env python3
"""Drive the Customer Display phase for a live demo.

Usage:  python3 scripts/cds-demo.py <welcome|order|tip|processing|success|receipt>

Pushes a CDS_STATE for terminal 'cashier-1' pointing at a seeded order, exactly as the
cashier app does during checkout. Watch the CDS at http://localhost:5273/cds/ follow along.
"""
import json
import sys
import time
import urllib.request

BASE = "http://localhost:8080"
PHASES = ["welcome", "order", "tip", "processing", "success", "receipt"]


def _req(path, tok=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if tok:
        headers["Authorization"] = "Bearer " + tok
    return json.load(urllib.request.urlopen(urllib.request.Request(BASE + path, data=data, headers=headers)))


def main():
    phase = (sys.argv[1] if len(sys.argv) > 1 else "welcome").lower()
    if phase not in PHASES:
        print(f"phase must be one of: {', '.join(PHASES)}")
        sys.exit(1)

    tok = _req("/auth/login/terminal", body={"terminalId": "cashier-1"})["token"]
    pull = _req("/sync/pull?since=0", tok=tok)
    items = {i["orderId"] for i in pull.get("orderItems", [])}
    candidates = [o["id"] for o in pull.get("orders", []) if o["id"] in items]
    order_id = candidates[0] if candidates else None
    if order_id is None and phase != "welcome":
        print("No seeded order with items found — run the seed step first.")
        sys.exit(1)

    ts = int(time.time() * 1000) + 10 ** 12  # always newest (last-write-wins)
    payload = json.dumps({
        "id": "cashier-1",
        "terminalId": "cashier-1",
        "orderId": None if phase == "welcome" else order_id,
        "phase": phase.upper(),
        "updatedAt": ts,
    })
    _req("/sync/push", tok=tok, body={
        "id": f"demo-{ts}", "entityType": "CDS_STATE", "entityId": "cashier-1",
        "operation": "UPDATE", "payload": payload, "updatedAt": ts,
    })
    print(f"CDS phase -> {phase.upper()}" + ("" if phase == "welcome" else f"  (order {order_id})"))


if __name__ == "__main__":
    main()
