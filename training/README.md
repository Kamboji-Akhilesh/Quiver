# Quiver AI — fine-tuning the one bundled model

Quiver ships exactly one model: **Gemma 3 1B fine-tuned on Quiver's tool-call
format, quantized to GGUF (Q4_K_M)**, running through the in-app llama.cpp
engine. The GBNF grammar makes invalid JSON *impossible* at decode time; the
fine-tune makes the plans *correct*. Training happens here (a PC or Colab GPU),
never on the phone.

## The sync contract (read this first)

Three artifacts encode the same format and MUST change together:

| Artifact | Where |
|---|---|
| Runtime prompt | `app/.../ai/agent/QuiverAgent.kt` (`prompt()`) |
| Training prompt mirror | `generate_dataset.py` (`TEMPLATE`, `SPEC_TEXT`, block builders) |
| Output grammar | `grammar/quiver_tools.gbnf` **and** `app/src/main/assets/quiver_tools.gbnf` |

The plan shape is `{"steps":[...],"reply":"..."}` — **steps first**. The chat
template is Gemma's (`<start_of_turn>user\n…<end_of_turn>\n<start_of_turn>model\n`),
applied by `LlamaCppEngine.applyTemplate()` at runtime and by the gemma-3
tokenizer template in training. If you add or change a tool in `AgentTools.kt`,
update `SPEC_TEXT` here, add seed examples for it, and retrain.

## Pipeline

```
dataset/seed.jsonl ──generate_dataset.py──▶ dataset.jsonl ──validate_dataset.py──▶ ✓
                                                                  │
                                                          finetune_lora.py
                                                                  │
                                        out/quiver-gemma-3-1b/…Q4_K_M.gguf
                                                                  │
                                     upload ──▶ AiModel.DOWNLOAD_URL ──▶ users tap Install
```

### 1. Grow the dataset

Two sources feed `dataset.jsonl` (~2,500 rows total):

- **Hand-written seeds** — `dataset/seed.jsonl`, the gold examples. Rendered
  against every reference date. **Add every real-world failure you hit here.**
- **Synthesized scenarios** — `scenario_bank.py` composes unique examples from
  per-language template banks (sentence patterns × items × dishes × people ×
  dates): ~750 English + ~250 each for hi / bn / ta / te / mr. It covers every
  tool, multi-step plans (note + reminder), and — most importantly — paired
  **web-search rounds**: round 1 must emit ONLY `web_search` even when the user
  asked for a note and reminder too; round 2 embeds the findings and finishes
  with the ingredients as a checklist plus the reminder. ~10% of action rows
  also get a **correction round** (recovery from a prose non-answer — the
  agent's retry pass).

To grow coverage, add templates/slots/dishes to `scenario_bank.py` (variety
comes free) and put nuanced or failed cases in `seed.jsonl` (quality anchors).
Keep tool args in the user's language — a Hindi request produces a Hindi note
title and reply.

```bash
python generate_dataset.py     # seeds + scenario_bank -> dataset.jsonl (~2500 rows)
python validate_dataset.py     # schema-checks every row; fails loudly
```

### 2. Train (Colab free T4 is enough)

```bash
pip install -r requirements.txt
python finetune_lora.py        # ~15 min for 1B on a T4
```

LoRA on `unsloth/gemma-3-1b-it` (ungated mirror of `google/gemma-3-1b-it`),
loss on the assistant turn only. The script ends by merging the adapter and
exporting `out/quiver-gemma-3-1b/*Q4_K_M.gguf` (~800 MB).

### 3. Evaluate — before AND after training

```bash
python eval.py --model out/quiver-gemma-3-1b/quiver-gemma-3-1b.Q4_K_M.gguf
```

`evalset.jsonl` is a held-out set of hand-written prompts (fresh phrasings that
never appear in the training templates — keep it that way). The script runs
each through llama-cli with the app's grammar + Gemma template and scores the
tool plans per language. Run it on the stock model first to get a baseline; a
fine-tune that doesn't beat the baseline doesn't ship. Failures print at the
bottom — the hard ones belong in `dataset/seed.jsonl` for the next round.

### 3b. Smoke-test the GGUF before shipping

Use the llama.cpp checked out in this repo (`app/src/main/cpp/llama.cpp`) —
build the CLI on your PC, then run a real agent prompt through the grammar:

```bash
llama-cli -m out/quiver-gemma-3-1b/quiver-gemma-3-1b.Q4_K_M.gguf \
  --grammar-file grammar/quiver_tools.gbnf \
  -p "<start_of_turn>user\n<paste a prompt printed by generate_dataset.py><end_of_turn>\n<start_of_turn>model\n" \
  -n 512 --temp 0.2
```

Keep a handful of held-out requests (including Hindi/Tamil ones and one
web-search round) and eyeball the plans every time you retrain — that's your
regression suite.

If `save_pretrained_gguf` ever breaks, the manual export is:
`llama.cpp/convert_hf_to_gguf.py <merged-dir> --outfile f16.gguf` then
`llama-quantize f16.gguf out.gguf Q4_K_M`.

### 4. Host the file

Where the model lives **on the phone**: the app downloads it into
`filesDir/llm-models/` — app-private storage, no permissions needed, invisible
to other apps, deleted on uninstall. (If you later publish on Play and switch
to Play Asset Delivery, Google hosts the pack and installs it into app-private
storage the same way — no public URL of yours involved at all.)

Where the file is served **from** — pick one:

- **Hugging Face (recommended now).** The copyright worry is a misconception:
  Gemma's license (the *Gemma Terms of Use*) explicitly allows publishing
  fine-tuned derivatives. Requirements: state it's a Gemma derivative, include
  a copy of the Terms/use-restrictions with the repo, and don't name it just
  "Gemma" as if it were Google's. HF is full of Gemma fine-tunes — this is the
  normal, intended path. Free bandwidth, resumable downloads.
- **GitHub Releases.** Up to 2 GB per file — the Q4 build fits. Fine for the
  sideload phase; same license-notice requirement applies.
- **Cloudflare R2 / any object store.** If you want it fully private-ish
  (unlisted URL). R2 has zero egress fees.
- **Play Asset Delivery** once the app is on Play — then the download URL and
  hosting question disappear entirely.

### 5. Point the app at it

Update `app/.../ai/AiModel.kt`: `DOWNLOAD_URL`, and `SIZE_LABEL`/`APPROX_BYTES`
if the file size changed meaningfully. Bump `FILE_NAME` (e.g. `-v2`) when you
ship a retrained model so existing installs re-download it (the ModelManager
migration deletes files that don't match the current name).
