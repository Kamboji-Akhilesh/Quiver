"""Expand hand-written seed examples into the Quiver fine-tuning set.

Reads dataset/seed.jsonl and writes dataset.jsonl in chat-messages format:
    {"messages":[{"role":"user","content":<full agent prompt>},
                 {"role":"assistant","content":<tool-call JSON>}], "kind": ...}

The user message is the EXACT prompt QuiverAgent builds at runtime
(app/src/main/java/com/kamboji/quiver/ai/agent/QuiverAgent.kt — keep TEMPLATE,
SPEC_TEXT and the block builders in byte-for-byte sync with it), so the model
sees identical text in training and production.

Three kinds of rows are produced:
  normal      seed rendered against several reference "today" dates, so the
              model learns to resolve relative dates instead of memorizing one
  findings    second agent round: the prompt embeds web-search results and the
              model must finish the task without calling web_search again
  correction  retry round: the prompt embeds a prose non-answer and the model
              must produce the JSON plan (trains the agent's recovery pass)

Seed row schema (dataset/seed.jsonl, one JSON object per line):
  user      the user's request (may contain {today}/{tomorrow}/{day_after}/
            {friday}/{monday}/{saturday} date placeholders)
  output    {"steps":[...], "reply": "..."} gold plan, same placeholders allowed
  findings  optional: web-search digest text -> renders a findings round
"""

from __future__ import annotations

import json
import random
from collections import Counter
from datetime import date, timedelta
from pathlib import Path

from scenario_bank import synthesize

HERE = Path(__file__).parent
SEED = HERE / "dataset" / "seed.jsonl"
OUT = HERE / "dataset.jsonl"

# Synthesized rows per language (scenario_bank.py). English carries the bulk;
# each Indic language gets full coverage of every scenario type.
QUOTAS = {"en": 750, "hi": 250, "bn": 250, "ta": 250, "te": 250, "mr": 250}
# Fraction of synthesized action rows that also get a correction-round variant.
SYNTH_CORRECTION_RATE = 0.10

# Reference "today" dates to render relative placeholders against.
ANCHORS = [
    date(2026, 3, 3),
    date(2026, 7, 1),
    date(2026, 11, 20),
    date(2027, 1, 15),
    date(2027, 5, 9),
    date(2027, 9, 28),
]
CLOCK_TIMES = ["08:12", "09:30", "11:45", "13:05", "16:40", "19:20", "21:37"]
ZONE = "Asia/Kolkata"

# Every Nth normal row also gets a correction-round variant.
CORRECTION_EVERY = 3

# --- Exact mirrors of the runtime prompt (QuiverAgent.kt) ------------------

# AgentTools.specText() — one line per tool, verbatim from AgentTools.kt.
SPEC_TEXT = "\n".join([
    '- web_search(query: string) — search the web. The results come back to you in a follow-up turn so you can finish the task with real, current information.',
    '- add_note(title: string, body: string) — create a note. Put each checklist item on its own line as "- [ ] item". Use \\n between lines.',
    '- append_note(title_contains: string, text: string) — append text to the most recent note whose title contains the given words.',
    '- add_task(title: string, date: "YYYY-MM-DD", time: "HH:mm") — add a to-do with a reminder.',
    '- add_event(title: string, date: "YYYY-MM-DD", time: "HH:mm") — add a calendar event with a reminder.',
    '- check_trash() — report how many screenshots are in the trash and name a few recent ones.',
    '- get_rate(from: string, to: string) — the exchange rate between two 3-letter currency codes.',
    '- convert(amount: number, from: string, to: string) — convert an amount between currencies.',
    '- list_agenda(date: "YYYY-MM-DD") — list the user\'s tasks and events on that date. The results come back to you so you can answer or act on them.',
    '- read_note(title_contains: string) — read the most recent note whose title contains the given words. Its content comes back to you so you can answer or update it.',
    '- add_expense(amount: number, category: string, note: string, date: "YYYY-MM-DD") — record money spent (₹). Category is one of: food, groceries, transport, shopping, bills, health, entertainment, other. Date is optional (today if omitted).',
    '- list_expenses(period: "YYYY-MM") — the user\'s spending for that month: total, per-category breakdown and recent entries. The results come back to you so you can answer. Period is optional (this month if omitted).',
])

# QuiverAgent.prompt() template (post-trimIndent), tokens substituted below.
TEMPLATE = r'''You are Quiver AI, a helpful assistant inside a phone app. You can perform actions by calling tools.

Available tools:
@TOOLS@

Context: today is @DATE@ (@DOW@), the current time is @TIME@, timezone @ZONE@.

When the user asks you to do something, reply with ONLY a JSON object, no prose and no markdown fences, in exactly this shape:
{"steps":[{"tool":"<tool name>","args":{ ... }}],"reply":"<short friendly confirmation for the user>"}

Rules:
- Resolve relative dates yourself from today's date. Use "date":"YYYY-MM-DD" and 24h "time":"HH:mm". Dayparts: morning=09:00, afternoon=14:00, evening=18:00, night=20:00.
- When the task needs information from the internet or facts you are unsure about (recipes, how-tos, prices, current facts), call web_search first — its results come back to you and you can then finish the task. Content you know well you may write yourself. Checklist items are lines like "- [ ] item".
- To answer questions about the user's own calendar, notes or spending, call list_agenda, read_note or list_expenses first — their content comes back to you the same way.
- You may use several steps in order. To add content under an existing note, use append_note.
- If no action is needed (just chatting), return "steps":[] and put your answer in "reply".
- Output JSON only.
@BLOCKS@
Example user: "Save a shopping list of tomatoes and pasta, and remind me tomorrow evening to buy them."
Example output:
{"steps":[{"tool":"add_note","args":{"title":"Shopping list","body":"- [ ] Tomatoes\n- [ ] Pasta"}},{"tool":"add_task","args":{"title":"Buy groceries","date":"@DATE@","time":"18:00"}}],"reply":"Saved your shopping list and a reminder for tomorrow evening."}

User: @USER@
JSON:'''


def findings_block(findings: str) -> str:
    return (
        "\nYou already ran the information tools. Results:\n" + findings.strip() + "\n\n"
        + "Now finish the user's request using these results (answer, or write the notes / tasks / events). Do not call web_search, list_agenda, read_note or list_expenses again.\n"
    )


def correction_block(bad_attempt: str) -> str:
    return (
        "\nYour previous answer was rejected because it was prose instead of the required JSON. It said:\n"
        + bad_attempt.strip()[:400]
        + "\nAnswer again with ONLY the JSON object in the shape above, starting with '{'. "
        + "Every action the user asked for must be a step — never describe steps in text.\n"
    )


def build_prompt(user: str, today: date, clock: str, findings: str | None = None, bad: str | None = None) -> str:
    blocks = ""
    if findings is not None:
        blocks += findings_block(findings)
    if bad is not None:
        blocks += correction_block(bad)
    return (
        TEMPLATE
        .replace("@TOOLS@", SPEC_TEXT)
        .replace("@DATE@", today.isoformat())
        .replace("@DOW@", today.strftime("%A"))
        .replace("@TIME@", clock)
        .replace("@ZONE@", ZONE)
        .replace("@BLOCKS@", blocks)
        .replace("@USER@", user)
    )


# --- Placeholder rendering ---------------------------------------------------

def placeholders(today: date) -> dict:
    def next_weekday(d: date, weekday: int) -> date:
        return d + timedelta(days=(weekday - d.weekday()) % 7 or 7)

    return {
        "{today}": today.isoformat(),
        "{tomorrow}": (today + timedelta(days=1)).isoformat(),
        "{day_after}": (today + timedelta(days=2)).isoformat(),
        "{friday}": next_weekday(today, 4).isoformat(),
        "{monday}": next_weekday(today, 0).isoformat(),
        "{saturday}": next_weekday(today, 5).isoformat(),
        "{this_month}": today.strftime("%Y-%m"),
        "{last_month}": (today.replace(day=1) - timedelta(days=1)).strftime("%Y-%m"),
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


def completion(output: dict) -> str:
    # steps first, then reply — the shape the grammar enforces at runtime.
    ordered = {"steps": output["steps"], "reply": output["reply"]}
    return json.dumps(ordered, ensure_ascii=False, separators=(",", ":"))


# --- Correction-round synthesis ----------------------------------------------

STEP_LABELS = {
    "web_search": "Search the web",
    "add_note": "Add a note",
    "append_note": "Update the note",
    "add_task": "Set a reminder",
    "add_event": "Add the event",
    "check_trash": "Check the trash",
    "get_rate": "Check the exchange rate",
    "convert": "Convert the amount",
}


def prose_attempt(output: dict) -> str:
    """A plausible prose non-answer, shaped like what small models emit."""
    if not output["steps"]:
        return "Okay! " + output["reply"]
    lines = "\n".join(f"- [ ] {STEP_LABELS.get(s['tool'], s['tool'])}" for s in output["steps"])
    return "Okay! I will do that for you. Let me check what's available.\n\nSteps:\n" + lines


# --- Main ---------------------------------------------------------------------

def emit_rows(ex: dict, today: date, clock: str, want_correction: bool) -> list[dict]:
    """One seed/synth instance -> dataset rows (plus optional correction round)."""
    subs = placeholders(today)
    user = render(ex["user"], subs)
    output = render(ex["output"], subs)
    target = completion(output)
    lang = ex.get("lang", "seed")

    if "findings" in ex:
        kind = "findings"
        prompt = build_prompt(user, today, clock, findings=render(ex["findings"], subs))
    else:
        kind = "normal"
        prompt = build_prompt(user, today, clock)
    out = [{"kind": kind, "lang": lang, "messages": [
        {"role": "user", "content": prompt},
        {"role": "assistant", "content": target},
    ]}]

    # Recovery training: same request, but the prompt carries a rejected prose
    # attempt (mirrors QuiverAgent's retry pass).
    if kind == "normal" and want_correction and output["steps"]:
        out.append({"kind": "correction", "lang": lang, "messages": [
            {"role": "user", "content": build_prompt(user, today, clock, bad=prose_attempt(output))},
            {"role": "assistant", "content": target},
        ]})
    return out


def main() -> None:
    rng = random.Random(7)
    rows = []
    by_kind: Counter = Counter()
    by_lang: Counter = Counter()

    # Hand-written seeds: rendered against EVERY anchor date (they're the gold
    # examples, so squeeze full date-resolution mileage out of each).
    seeds = [json.loads(l) for l in SEED.read_text(encoding="utf-8").splitlines() if l.strip()]
    for i, ex in enumerate(seeds):
        for today in ANCHORS:
            for row in emit_rows(ex, today, rng.choice(CLOCK_TIMES), want_correction=(i % CORRECTION_EVERY == 0)):
                rows.append(row)
                by_kind[row["kind"]] += 1
                by_lang[row["lang"]] += 1

    # Synthesized scenarios: each unique instance gets ONE random anchor, so the
    # volume comes from scenario/slot variety rather than date repetition.
    for ex in synthesize(rng, QUOTAS):
        for row in emit_rows(ex, rng.choice(ANCHORS), rng.choice(CLOCK_TIMES),
                             want_correction=(rng.random() < SYNTH_CORRECTION_RATE)):
            rows.append(row)
            by_kind[row["kind"]] += 1
            by_lang[row["lang"]] += 1

    rng.shuffle(rows)
    with OUT.open("w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"Wrote {len(rows)} examples to {OUT}")
    print(f"  kinds: {dict(by_kind)}")
    print(f"  langs: {dict(by_lang)}")


if __name__ == "__main__":
    main()
