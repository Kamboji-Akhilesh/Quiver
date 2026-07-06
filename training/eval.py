"""Score a GGUF build against the held-out eval set — the missing half of the
training loop. Run it on the base model before training and on every fine-tune
after; if the pass-rate didn't go up, don't ship the new model.

    python eval.py --model out/quiver-gemma-3-1b/quiver-gemma-3-1b.Q4_K_M.gguf
    python eval.py --dump           # just write the prompts for manual runs

Needs a llama.cpp CLI binary (build it from app/src/main/cpp/llama.cpp, or
`--llama-cli path/to/llama-cli`). Prompts are built by generate_dataset.py's
mirror of the runtime prompt, wrapped in the same Gemma template the app uses,
and decoded under the same GBNF grammar — so a pass here means the same tokens
would have worked on the phone.

evalset.jsonl cases are HAND-WRITTEN with phrasings that do not appear in the
training templates (that's the point — don't copy bank sentences into it).
Matching: expected steps must all be present (any order, count must match);
arg values like "*substring*" match case-insensitively, {tomorrow}-style
placeholders resolve against --today, everything else compares exactly.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from collections import defaultdict
from datetime import date
from pathlib import Path

from generate_dataset import build_prompt, placeholders

HERE = Path(__file__).parent
GRAMMAR = HERE / "grammar" / "quiver_tools.gbnf"
GEMMA_TEMPLATE = "<start_of_turn>user\n{p}<end_of_turn>\n<start_of_turn>model\n"


def resolve(value, subs: dict):
    if isinstance(value, str):
        for k, v in subs.items():
            value = value.replace(k, v)
    return value


def step_matches(pred: dict, exp: dict, subs: dict) -> bool:
    if pred.get("tool") != exp["tool"]:
        return False
    args = pred.get("args", {})
    for key, want in exp.get("args_contains", {}).items():
        got = args.get(key)
        want = resolve(want, subs)
        if isinstance(want, str) and want.startswith("*") and want.endswith("*"):
            if not isinstance(got, str) or want.strip("*").lower() not in got.lower():
                return False
        elif isinstance(want, (int, float)):
            if not isinstance(got, (int, float)) or float(got) != float(want):
                return False
        elif str(got) != str(want):
            return False
    return True


def plan_error(plan: dict, expect: list, subs: dict) -> str | None:
    """None if the plan satisfies the expectation, else a short reason."""
    steps = plan.get("steps")
    if not isinstance(steps, list):
        return "no steps array"
    if len(steps) != len(expect):
        return f"expected {len(expect)} step(s), got {len(steps)}"
    used = [False] * len(steps)
    for exp in expect:
        hit = next(
            (i for i, s in enumerate(steps)
             if not used[i] and isinstance(s, dict) and step_matches(s, exp, subs)),
            None,
        )
        if hit is None:
            return f"no step matching {json.dumps(exp, ensure_ascii=False)}"
        used[hit] = True
    return None


def extract_json(text: str) -> dict | None:
    i, j = text.find("{"), text.rfind("}")
    if i < 0 or j <= i:
        return None
    try:
        return json.loads(text[i:j + 1])
    except json.JSONDecodeError:
        return None


def run_model(cli: str, model: str, prompt: str, timeout: int) -> str:
    cmd = [
        cli, "-m", model,
        "--grammar-file", str(GRAMMAR),
        "-n", "512", "--temp", "0.2",
        "--no-display-prompt", "-no-cnv", "--simple-io",
        "-p", GEMMA_TEMPLATE.format(p=prompt),
    ]
    out = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", timeout=timeout)
    return out.stdout or ""


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--model", help="GGUF file to evaluate")
    ap.add_argument("--llama-cli", default="llama-cli", help="llama.cpp CLI binary")
    ap.add_argument("--today", default="2027-03-10", help="reference date for the prompts")
    ap.add_argument("--timeout", type=int, default=300, help="seconds per case")
    ap.add_argument("--dump", action="store_true", help="write prompts to eval_prompts/ and exit")
    args = ap.parse_args()

    today = date.fromisoformat(args.today)
    subs = placeholders(today)
    cases = [json.loads(l) for l in (HERE / "evalset.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]

    if args.dump:
        out_dir = HERE / "eval_prompts"
        out_dir.mkdir(exist_ok=True)
        for n, case in enumerate(cases, 1):
            prompt = build_prompt(case["user"], today, "11:00", findings=resolve(case.get("findings"), subs))
            (out_dir / f"{n:02d}_{case['lang']}.txt").write_text(prompt, encoding="utf-8")
        print(f"Wrote {len(cases)} prompts to {out_dir}")
        return

    if not args.model:
        ap.error("--model is required (or use --dump)")

    passed: dict = defaultdict(int)
    total: dict = defaultdict(int)
    failures = []
    for n, case in enumerate(cases, 1):
        lang = case["lang"]
        total[lang] += 1
        prompt = build_prompt(case["user"], today, "11:00", findings=resolve(case.get("findings"), subs))
        raw = run_model(args.llama_cli, args.model, prompt, args.timeout)
        plan = extract_json(raw)
        err = "no JSON in output" if plan is None else plan_error(plan, case["expect"], subs)
        if err is None:
            passed[lang] += 1
            print(f"  ok  [{lang}] {case['user'][:60]}")
        else:
            failures.append((case, err, raw.strip()[:300]))
            print(f"FAIL  [{lang}] {case['user'][:60]}  — {err}")

    print("\n=== Results ===")
    for lang in sorted(total):
        print(f"  {lang}: {passed[lang]}/{total[lang]}")
    overall_pass, overall = sum(passed.values()), sum(total.values())
    print(f"  overall: {overall_pass}/{overall} ({100 * overall_pass // max(overall, 1)}%)")
    if failures:
        print("\n=== Failures (add the hard ones to dataset/seed.jsonl!) ===")
        for case, err, raw in failures:
            print(f"\n[{case['lang']}] {case['user']}\n  reason: {err}\n  output: {raw}")


if __name__ == "__main__":
    main()
