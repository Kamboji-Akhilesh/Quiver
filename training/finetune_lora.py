"""LoRA fine-tune Gemma for Quiver tool-calling (run on a CUDA GPU / Colab).

Wraps each example in the SAME prompt the app uses at inference time, so the model
sees the identical format in training and production. KEEP PROMPT_PREAMBLE IN SYNC
with app/src/main/java/com/kamboji/quiver/ai/agent/QuiverAgent.kt.

    pip install -r requirements.txt
    python generate_dataset.py
    python finetune_lora.py
"""

import json
from pathlib import Path

from datasets import load_dataset
from trl import SFTConfig, SFTTrainer
from unsloth import FastLanguageModel

BASE_MODEL = "google/gemma-3-1b-it"   # target the variant you run on-device
MAX_SEQ_LEN = 2048
OUT_DIR = "out/quiver-lora"

# Tool list — mirror AgentTools.specText(). Update when you add/change tools.
TOOLS = """- add_note(title, body) — create a note; checklist items are lines "- [ ] item".
- append_note(title_contains, text) — append text to the newest matching note.
- add_task(title, date "YYYY-MM-DD", time "HH:mm") — a to-do with a reminder.
- add_event(title, date "YYYY-MM-DD", time "HH:mm") — a calendar event with a reminder.
- check_trash() — how many screenshots are in the trash.
- get_rate(from, to) — exchange rate between two currency codes.
- convert(amount, from, to) — convert an amount between currencies."""

PROMPT_PREAMBLE = """You are Quiver AI, a helpful assistant inside a phone app. You can perform actions by calling tools.

Available tools:
{tools}

Context: today is {today}.

When the user asks you to do something, reply with ONLY a JSON object, no prose and no markdown fences, in exactly this shape:
{{"reply":"<short friendly confirmation>","steps":[{{"tool":"<name>","args":{{ ... }}}}]}}

Rules:
- Resolve relative dates from today. Use "date":"YYYY-MM-DD" and 24h "time":"HH:mm". morning=09:00, afternoon=14:00, evening=18:00, night=20:00.
- Generate requested content (recipes, lists) yourself into the tool arguments. Checklist items are lines like "- [ ] item".
- If no action is needed, return "steps":[] and put the answer in "reply".
- Output JSON only.

User: {user}
JSON:"""


def format_example(row) -> dict:
    prompt = PROMPT_PREAMBLE.format(tools=TOOLS, today=row["today"], user=row["user"])
    return {"text": prompt + " " + row["output"]}


def main() -> None:
    data_path = Path(__file__).parent / "dataset.jsonl"
    ds = load_dataset("json", data_files=str(data_path), split="train").map(format_example)

    model, tokenizer = FastLanguageModel.from_pretrained(
        model_name=BASE_MODEL, max_seq_length=MAX_SEQ_LEN, load_in_4bit=True,
    )
    model = FastLanguageModel.get_peft_model(
        model, r=16, lora_alpha=16, lora_dropout=0.0,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
    )

    trainer = SFTTrainer(
        model=model,
        tokenizer=tokenizer,
        train_dataset=ds,
        args=SFTConfig(
            dataset_text_field="text",
            max_seq_length=MAX_SEQ_LEN,
            per_device_train_batch_size=2,
            gradient_accumulation_steps=4,
            warmup_steps=5,
            num_train_epochs=3,
            learning_rate=2e-4,
            logging_steps=10,
            output_dir=OUT_DIR,
            optim="adamw_8bit",
        ),
    )
    trainer.train()
    model.save_pretrained(OUT_DIR)
    tokenizer.save_pretrained(OUT_DIR)
    print(f"Saved LoRA adapter to {OUT_DIR}")
    print("Next: merge + convert to .task (AI Edge Torch) or export GGUF, or load the adapter at runtime.")


if __name__ == "__main__":
    main()
