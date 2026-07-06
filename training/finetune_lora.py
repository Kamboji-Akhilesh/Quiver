"""LoRA fine-tune Gemma 3 1B for Quiver tool-calling, then export GGUF.

Run on a CUDA GPU — a free Colab T4 does this in ~15 minutes:

    pip install -r requirements.txt
    python generate_dataset.py
    python validate_dataset.py
    python finetune_lora.py

The dataset rows are chat messages whose user content is the EXACT prompt the
app builds at runtime (QuiverAgent.kt). Training applies the gemma-3 chat
template — "<start_of_turn>user\\n…<end_of_turn>\\n<start_of_turn>model\\n…" —
which is byte-identical to what LlamaCppEngine.applyTemplate() wraps around the
prompt on-device, so the model sees the same text in training and production.
Loss is computed on the assistant turn only (train_on_responses_only).

Output:
    out/quiver-lora/            LoRA adapter (keep, for iterating later)
    out/quiver-gemma-3-1b/      merged model + quiver-gemma-3-1b.Q4_K_M.gguf
                                → upload this .gguf and point AiModel.DOWNLOAD_URL at it
"""

from pathlib import Path

from datasets import load_dataset
from trl import SFTConfig, SFTTrainer
from unsloth import FastModel
from unsloth.chat_templates import get_chat_template, train_on_responses_only

# unsloth/gemma-3-1b-it is an ungated mirror of google/gemma-3-1b-it; use the
# google/ repo instead if you've accepted the Gemma license on Hugging Face.
BASE_MODEL = "unsloth/gemma-3-1b-it"
MAX_SEQ_LEN = 2048
OUT = Path("out")


def main() -> None:
    model, tokenizer = FastModel.from_pretrained(
        model_name=BASE_MODEL, max_seq_length=MAX_SEQ_LEN, load_in_4bit=True,
    )
    model = FastModel.get_peft_model(
        model, r=16, lora_alpha=16, lora_dropout=0.0, random_state=3407,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
    )
    tokenizer = get_chat_template(tokenizer, chat_template="gemma-3")

    data = Path(__file__).parent / "dataset.jsonl"
    ds = load_dataset("json", data_files=str(data), split="train").map(
        lambda row: {"text": tokenizer.apply_chat_template(row["messages"], tokenize=False, add_generation_prompt=False)},
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
            warmup_steps=10,
            num_train_epochs=3,
            learning_rate=2e-4,
            logging_steps=10,
            output_dir=str(OUT / "checkpoints"),
            optim="adamw_8bit",
            seed=3407,
        ),
    )
    # Mask the prompt: learn only the assistant's JSON, not to parrot the prompt.
    trainer = train_on_responses_only(
        trainer,
        instruction_part="<start_of_turn>user\n",
        response_part="<start_of_turn>model\n",
    )
    trainer.train()

    adapter_dir = OUT / "quiver-lora"
    model.save_pretrained(str(adapter_dir))
    tokenizer.save_pretrained(str(adapter_dir))
    print(f"Saved LoRA adapter to {adapter_dir}")

    # Merge the adapter and export a quantized GGUF for the app's llama.cpp
    # engine. If this step ever breaks (it compiles llama.cpp under the hood),
    # the manual route is in README step 4 using the repo's own llama.cpp.
    model.save_pretrained_gguf(str(OUT / "quiver-gemma-3-1b"), tokenizer, quantization_method="q4_k_m")
    print(f"GGUF written under {OUT / 'quiver-gemma-3-1b'} — upload it and update AiModel.DOWNLOAD_URL.")


if __name__ == "__main__":
    main()
