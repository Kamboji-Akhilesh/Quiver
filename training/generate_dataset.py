"""Expand hand-written seed examples into a training set.

Reads dataset/seed.jsonl (user -> output-JSON, with {tomorrow}/{day_after}/{friday}
date placeholders) and writes dataset.jsonl with each example rendered against
several reference "today" dates, so the model learns to resolve relative dates
rather than memorize one. Add more augmentation (currencies, titles) as needed.
"""

import json
import random
from datetime import date, timedelta
from pathlib import Path

HERE = Path(__file__).parent
SEED = HERE / "dataset" / "seed.jsonl"
OUT = HERE / "dataset.jsonl"

# Reference "today" dates to render relative placeholders against.
ANCHORS = [date(2026, 3, 3), date(2026, 7, 1), date(2026, 11, 20), date(2027, 1, 15)]


def placeholders(today: date) -> dict:
    def next_weekday(d: date, weekday: int) -> date:
        return d + timedelta(days=(weekday - d.weekday()) % 7 or 7)

    return {
        "{today}": today.isoformat(),
        "{tomorrow}": (today + timedelta(days=1)).isoformat(),
        "{day_after}": (today + timedelta(days=2)).isoformat(),
        "{friday}": next_weekday(today, 4).isoformat(),
    }


def render(obj, subs: dict):
    if isinstance(obj, str):
        for k, v in subs.items():
            obj = obj.replace(k, v)
        return obj
    if isinstance(obj, list):
        return [render(x, subs) for x in obj]
    if isinstance(obj, dict):
        return {k: render(v, subs) for k, v in obj.items()}
    return obj


def main() -> None:
    seeds = [json.loads(l) for l in SEED.read_text(encoding="utf-8").splitlines() if l.strip()]
    rows = []
    for ex in seeds:
        for today in ANCHORS:
            subs = placeholders(today)
            rows.append({
                "today": today.isoformat(),
                "user": render(ex["user"], subs),
                "output": json.dumps(render(ex["output"], subs), ensure_ascii=False),
            })
    random.shuffle(rows)
    with OUT.open("w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"Wrote {len(rows)} examples to {OUT}")


if __name__ == "__main__":
    main()
