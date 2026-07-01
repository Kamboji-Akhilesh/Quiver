# Quiver AI — tool-calling fine-tune

Fine-tune a small Gemma model to reliably emit Quiver's tool-call JSON, then run
it on-device. **Training happens here (a PC/Colab GPU), not on the phone.** You
ship either a merged model or — better — a small LoRA adapter on top of the base
model the user already downloaded.

## Why this is the biggest quality lever
A generic 1B model is mediocre at emitting strict `{"reply","steps":[…]}` JSON for
our exact tools. A LoRA fine-tune on a few hundred task-specific examples makes a
1B model beat a generic 4B *at this task* — while staying tiny and fast.

## Pipeline

```
seed.jsonl ──generate_dataset.py──▶ dataset.jsonl ──finetune_lora.py──▶ LoRA adapter
                                                                            │
                                                            ┌───────────────┴───────────────┐
                                                            ▼                               ▼
                                              merge + convert to .task            ship adapter, load at runtime
                                              (AI Edge Torch / MediaPipe)         (LlmInferenceSession LoRA)
```

1. **Dataset.** `seed.jsonl` holds hand-written `(user → output-JSON)` examples for
   every tool, multi-step requests, relative dates, and target languages. Run
   `generate_dataset.py` to augment them (date/title/currency variations) into a
   few hundred–thousand rows.
2. **Train.** `finetune_lora.py` LoRA-tunes Gemma (Unsloth + TRL). ~10–20 min on a
   free Colab T4 for 1B. **The prompt template MUST match the app** — see
   `PROMPT_PREAMBLE` in the script, kept in sync with
   `app/.../ai/agent/QuiverAgent.kt`.
3. **Ship.** Two options:
   - **LoRA adapter (recommended):** convert the adapter and load it at runtime via
     MediaPipe `LlmInferenceSessionOptions` LoRA path (base model stays a normal
     user download; adapter is a few MB bundled in the app).
   - **Merged model:** merge LoRA into the base, then convert to `.task` with
     [AI Edge Torch](https://github.com/google-ai-edge/ai-edge-torch) generative
     conversion, or export GGUF (`llama.cpp/convert_hf_to_gguf.py`) for the
     llama.cpp engine.

## Reliability without training (do this too)
If you add the **llama.cpp / GGUF engine**, use **GBNF grammar-constrained
decoding** so the model *cannot* emit invalid JSON. Grammar + a fine-tuned adapter
together is the most robust setup. `grammar/quiver_tools.gbnf` is a starting schema.

## Setup
```bash
pip install -r requirements.txt
python generate_dataset.py            # seed.jsonl -> dataset.jsonl
python finetune_lora.py               # dataset.jsonl -> out/quiver-lora
```
Needs a CUDA GPU (Colab is fine). Set `BASE_MODEL` in `finetune_lora.py` to the
Gemma variant you target on-device (e.g. `google/gemma-3-1b-it`).
