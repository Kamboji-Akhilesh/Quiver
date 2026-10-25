"""Validate dataset.jsonl before training.

Every assistant answer must be a plan the app could actually execute: valid
compact JSON in the {"steps":[...],"reply":"..."} shape (steps first — the GBNF
grammar enforces that order at runtime), known tools only, well-formed args.
Run this after generate_dataset.py and after any hand edit to the seeds:

    python validate_dataset.py [path/to/dataset.jsonl]
"""

from __future__ import annotations

import json
import re
import sys
from collections import Counter
from pathlib import Path

DATASET = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent / "dataset.jsonl"

DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
TIME_RE = re.compile(r"^([01]\d|2[0-3]):[0-5]\d$")
CODE_RE = re.compile(r"^[A-Z]{3}$")
PLACEHOLDER_RE = re.compile(r"\{(today|tomorrow|day_after|friday|monday|saturday|this_month|last_month)\}")

TOOLS = {"web_search", "add_note", "append_note", "add_task", "add_event", "check_trash", "get_rate", "convert",
         "list_agenda", "read_note", "add_expense", "list_expenses", "search_screenshots", "add_rate_alert"}

CATEGORIES = {"food", "groceries", "transport", "shopping", "bills", "health", "entertainment", "other"}
PERIOD_RE = re.compile(r"^\d{4}-\d{2}$")

# Tools that must only appear in round 1 (their output feeds the next round).
INFO_TOOLS = {"web_search", "list_agenda", "read_note", "list_expenses", "search_screenshots"}


def check_step(step: dict) -> str | None:
    if set(step.keys()) != {"tool", "args"}:
        return f"step keys must be exactly tool+args, got {sorted(step.keys())}"
    tool, args = step["tool"], step["args"]
    if tool not in TOOLS:
        return f"unknown tool {tool!r}"
    if not isinstance(args, dict):
        return "args must be an object"
    if tool == "web_search" and not str(args.get("query", "")).strip():
        return "web_search needs a non-empty query"
    if tool == "add_note" and not (str(args.get("title", "")).strip() or str(args.get("body", "")).strip()):
        return "add_note needs a title or body"
    if tool == "append_note" and not str(args.get("text", "")).strip():
        return "append_note needs text"
    if tool in ("add_task", "add_event"):
        if not str(args.get("title", "")).strip():
            return f"{tool} needs a title"
        if not DATE_RE.match(str(args.get("date", ""))):
            return f"{tool} date must be YYYY-MM-DD, got {args.get('date')!r}"
        if not TIME_RE.match(str(args.get("time", ""))):
            return f"{tool} time must be HH:mm (24h), got {args.get('time')!r}"
    if tool == "get_rate":
        if not (CODE_RE.match(str(args.get("from", ""))) and CODE_RE.match(str(args.get("to", "")))):
            return "get_rate needs 3-letter uppercase from/to codes"
    if tool == "convert":
        if not isinstance(args.get("amount"), (int, float)):
            return "convert amount must be a number"
        if not (CODE_RE.match(str(args.get("from", ""))) and CODE_RE.match(str(args.get("to", "")))):
            return "convert needs 3-letter uppercase from/to codes"
    # Optional args mirror the RUNTIME contract: list_agenda's date defaults to
    # today, read_note's blank title_contains means "most recent note", and a
    # missing add_expense category parses to OTHER. The generator still emits
    # them explicitly as a matter of policy — but a plan the app would execute
    # must not fail validation.
    if tool == "list_agenda" and "date" in args and not DATE_RE.match(str(args["date"])):
        return f"list_agenda date must be YYYY-MM-DD when present, got {args.get('date')!r}"
    if tool == "read_note" and "title_contains" in args and not isinstance(args["title_contains"], str):
        return "read_note title_contains must be a string"
    if tool == "add_expense":
        amt = args.get("amount")
        if not isinstance(amt, (int, float)) or amt <= 0:
            return f"add_expense amount must be a positive number, got {amt!r}"
        # Category is optional (runtime defaults to OTHER), but when the training
        # data DOES name one it must be canonical — targets should teach the
        # documented ids, not free-text the runtime happens to tolerate.
        if "category" in args and str(args["category"]) not in CATEGORIES:
            return f"add_expense category must be one of {sorted(CATEGORIES)} when present, got {args.get('category')!r}"
        if "date" in args and not DATE_RE.match(str(args["date"])):
            return f"add_expense date must be YYYY-MM-DD, got {args.get('date')!r}"
    if tool == "list_expenses" and "period" in args and not PERIOD_RE.match(str(args["period"])):
        return f"list_expenses period must be YYYY-MM, got {args.get('period')!r}"
    if tool == "add_rate_alert":
        if not (CODE_RE.match(str(args.get("from", ""))) and CODE_RE.match(str(args.get("to", "")))):
            return "add_rate_alert needs 3-letter uppercase from/to codes"
        thr = args.get("threshold")
        if not isinstance(thr, (int, float)) or thr <= 0:
            return f"add_rate_alert threshold must be a positive number, got {thr!r}"
    return None


def check_row(row: dict) -> list[str]:
    errs = []
    msgs = row.get("messages")
    if not (isinstance(msgs, list) and len(msgs) == 2 and msgs[0].get("role") == "user" and msgs[1].get("role") == "assistant"):
        return ["messages must be exactly [user, assistant]"]
    prompt, answer = msgs[0]["content"], msgs[1]["content"]

    if "User: " not in prompt or not prompt.endswith("JSON:"):
        errs.append("prompt doesn't look like the runtime agent prompt")
    if PLACEHOLDER_RE.search(prompt) or PLACEHOLDER_RE.search(answer):
        errs.append("unrendered {date} placeholder left in row")

    if not answer.startswith('{"steps":'):
        errs.append('completion must start with {"steps": (steps-first shape)')
    try:
        plan = json.loads(answer)
    except json.JSONDecodeError as e:
        return errs + [f"completion is not valid JSON: {e}"]

    if set(plan.keys()) != {"steps", "reply"}:
        errs.append(f"plan keys must be exactly steps+reply, got {sorted(plan.keys())}")
        return errs
    if not isinstance(plan["steps"], list):
        errs.append("steps must be a list")
        return errs
    if not str(plan["reply"]).strip():
        errs.append("reply must be non-empty")
    for i, step in enumerate(plan["steps"]):
        err = check_step(step) if isinstance(step, dict) else "step must be an object"
        if err:
            errs.append(f"step[{i}]: {err}")

    # A findings round must finish the job, not gather information again.
    if "You already ran the information tools" in prompt:
        if any(s.get("tool") in INFO_TOOLS for s in plan["steps"] if isinstance(s, dict)):
            errs.append("findings round must not call info tools again")
    return errs


def main() -> None:
    rows = [json.loads(l) for l in DATASET.read_text(encoding="utf-8").splitlines() if l.strip()]
    kinds = Counter(r.get("kind", "?") for r in rows)
    bad = 0
    for n, row in enumerate(rows, 1):
        for err in check_row(row):
            bad += 1
            user_line = next((l for l in row["messages"][0]["content"].splitlines() if l.startswith("User: ")), "")[:80]
            print(f"row {n}: {err}   [{user_line}]")
    print(f"\n{len(rows)} rows ({dict(kinds)}) — {bad} problem(s)")
    if bad:
        sys.exit(1)


if __name__ == "__main__":
    main()
