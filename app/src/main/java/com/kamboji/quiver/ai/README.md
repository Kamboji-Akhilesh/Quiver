# Quiver AI — the on-device assistant

The connective tissue of the app: a **local** language model that turns a plain
sentence ("save a shopping list of tomatoes and pasta and remind me tomorrow
evening") into real actions across the mini-apps — and does it entirely on the
phone.

> This README covers how the assistant **runs inside the app**. For how the model
> is **fine-tuned** (dataset, training, GGUF export), see
> [`training/README.md`](../../../../../../../../training/README.md).

## The problem
Cloud assistants are powerful but they send everything you say to a server, need
a connection, and can't touch your private notes or calendar. A phone-only
assistant, meanwhile, is usually a toy: a small model will happily *say* it added
a reminder without actually doing anything, or emit malformed JSON that crashes
the caller.

## The solution
A **hybrid** design that plays to a small model's strengths and guards its
weaknesses:

1. **The model only plans.** It emits a JSON plan of tool calls —
   `{"steps":[…],"reply":"…"}`. It writes the *content* you asked for (a recipe,
   a checklist) into tool arguments, but it never touches dates, storage or side
   effects.
2. **The plan is coerced back into shape.** Historically a GBNF grammar made bad
   JSON *impossible* — but the runtime now uses MediaPipe (see `engine/`), which
   has no grammar. Reliability instead comes from low-temperature decoding,
   `agent/PlanJson`'s repair pass (fixes truncation, raw control chars, an
   unterminated string) and a corrective retry round. Malformed plans are
   recovered, not merely tolerated — but they are no longer *impossible*.
3. **Code does the reliable part.** `QuiverAgent` parses the plan and executes
   each step deterministically against the real mini-app stores — so an
   AI-created note or reminder is identical to one you made by hand.

Result: a genuinely useful assistant that works offline, keeps your data on the
device, and can't silently no-op.

## How it works
```
user text
   │
   ▼
QuiverAgent.prompt()  ──▶  MediaPipeEngine (Gemma .task, GPU→CPU)  ──▶  JSON plan
   │                                                                        │
   │   round 1: run only INFO tools (web_search, list_agenda, read_note,    │
   │            list_expenses, search_screenshots) ─┐                       │
   │                                                │ results fed back      │
   │   round 2: write notes / tasks / events using the findings ◀──────────┘
   ▼
AgentTools  ──▶  NotesStore / CalendarStore / ExpenseStore / CurrencyRepo / …
```

- **`agent/QuiverAgent`** — the brain. Builds the planning prompt (today's date,
  the tool list, the output shape), runs up to **2 rounds**, and executes steps.
  Round 1 runs only *information* tools; their results come back as "findings" so
  round 2 can answer or act on **real** data instead of guesses. A per-round
  watchdog (4 min) turns a wedged model into an error instead of an endless
  "Thinking…". If the model answers in prose instead of JSON, one corrective pass
  usually fixes it.
- **`agent/AgentTools`** — the catalogue of capabilities. Each tool is backed by
  a real store, so its effects show up in the mini-apps. Current tools:
  `web_search`, `add_note`, `append_note`, `read_note`, `add_task`, `add_event`,
  `list_agenda`, `add_expense`, `list_expenses`, `check_trash`,
  `search_screenshots`, `get_rate`, `convert`, `add_rate_alert`.
- **`agent/WhenResolver`** / **`calendar/QuickAddParser`** — turn "tomorrow
  evening" / "next Friday 6pm" into concrete timestamps (weekday-aware; shared
  with quick-add and the share router).
- **`agent/WebSearch`** — a keyless web lookup so the model can ground answers
  (recipes, prices, facts) it isn't sure about.
- **`agent/AiAgentService`** — runs a request as a **foreground service** so it
  keeps going even if you close the app right after asking; progress is mirrored
  to an ongoing notification and, in-app, via `agent/AgentBus`.
- **`engine/`** — `MediaPipeEngine` runs the Gemma `.task` bundle on MediaPipe's
  LLM Inference runtime, trying the **GPU** first and falling back to CPU. It
  budgets `maxTokens` across input **and** output, and honours cancellation
  mid-generation, which is what makes the watchdog enforceable. `LlamaCppEngine`
  (JNI over llama.cpp, with the GBNF grammar) is still here for `.gguf` files;
  `EngineHolder` picks the backend from the model's extension and keeps it
  resident between messages so only the first request pays the load cost.
- **`AiModel` / `ModelManager` / `ModelDownloadWorker`** — one curated model
  (Gemma 3 1B, int4 `.task`, ~530 MB), installed with a single tap into
  app-private storage (`filesDir/llm-models/`, no permissions, gone on uninstall).
  Its HuggingFace repo is **gated**: the download sends `HF_TOKEN` (from
  `local.properties`) as a Bearer header.
- **`VoiceController`** — platform speech-to-text for talk-instead-of-type
  (shared with the Notes voice button). Nothing is recorded to disk.
- **`tts/`** — the reminder-call **voice**. `ReminderSpeaker` speaks via
  Cartesia cloud TTS when it's configured and reachable, and falls back to the
  on-device `TextToSpeech` otherwise, so a reminder always speaks — even offline.
  Audio plays on the alarm stream so it's actually heard. `ReminderScript` /
  `ReminderVoiceSettings` build and configure the spoken line.
- **`ui/ai/QuiverAiPanel`** — the chat surface: type or speak, watch each step
  execute live, and read the final reply.

## The sync contract (important)
Three artifacts encode the same tool-call format and **must change together**:

| Artifact | Where |
|---|---|
| Runtime planning prompt | `agent/QuiverAgent.kt` (`prompt()`) + `agent/AgentTools.kt` (`spec`s) |
| Training prompt mirror | `training/generate_dataset.py` |
| Tool whitelist / validator | `training/validate_dataset.py` |
| Output grammar (`.gguf` path only) | `training/grammar/quiver_tools.gbnf` **and** `app/src/main/assets/quiver_tools.gbnf` |

Add or change a tool and you update all of these, then add scenarios/eval cases
and retrain. See [`training/README.md`](../../../../../../../../training/README.md)
and the standing rule in [`docs/ROADMAP.md`](../../../../../../../../docs/ROADMAP.md).

> **Open gap after the MediaPipe switch:** `training/` exports a **GGUF**, but the
> runtime now loads a **`.task`**. Before shipping a fine-tune, add a MediaPipe
> `.task` bundling step to the pipeline (or move to LiteRT-LM, which MediaPipe's
> LLM Inference API now defers to).

## Key files
- `agent/` — `QuiverAgent`, `AgentTools`, `WhenResolver`, `WebSearch`,
  `AiAgentService`, `AgentBus`, `PlanJson`
- `engine/` — `MediaPipeEngine`, `LlamaCppEngine`, `EngineHolder`, `InferenceEngine`
- `tts/` — `ReminderSpeaker`, `CartesiaTts`, `ReminderScript`, `ReminderVoiceSettings`
- `AiModel.kt`, `ModelManager.kt`, `ModelDownloadWorker.kt`, `VoiceController.kt`
- `ui/ai/QuiverAiPanel.kt` — the chat UI

## Permissions
`RECORD_AUDIO` (voice input, optional), `INTERNET` (model download + optional
web search / Cartesia TTS), `POST_NOTIFICATIONS` (progress notification),
`FOREGROUND_SERVICE`.
