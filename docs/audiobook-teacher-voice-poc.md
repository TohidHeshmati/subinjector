# Audiobook teacher-voice proof of concept

* **Date:** 2026-10-17
* **Status:** Proof of concept done; validation phase (Phase 0 below) not started
* **Related:** [Roadmap — Phase 6: Audiobook pipeline](../ROADMAP.md), [ADR-003](adr/ADR-003-ai-provider-port-and-adapters.md), [ADR-007](adr/ADR-007-local-model-sizing.md), [Product overview](product.md)

This document records a manual proof of concept that tested whether Subinjector should grow from subtitles into audiobooks with a spoken "teacher" briefing. It covers what was built, every result and failure, the decisions made in discussion, and the proposed task list. No application code was written; everything ran as shell commands and small Python scripts outside this repository.

## Contents

1. [Question](#1-question)
2. [Setup](#2-setup)
3. [Pipeline and results, step by step](#3-pipeline-and-results-step-by-step)
4. [Pronunciation experiments](#4-pronunciation-experiments)
5. [Findings](#5-findings)
6. [Design discussion and decisions](#6-design-discussion-and-decisions)
7. [Should we build it?](#7-should-we-build-it)
8. [Proposed architecture and tech stack](#8-proposed-architecture-and-tech-stack)
9. [Task list](#9-task-list)
10. [Open questions](#10-open-questions)
11. [Appendix: artifacts, commands, prompts](#11-appendix-artifacts-commands-prompts)

---

## 1. Question

Is it worth extending Subinjector to audiobooks, where a teacher's voice gives the learner a short English briefing (words and grammar to watch for) before they listen to a section of a German audiobook?

Target learner for the test: **English speaker, German level B1.**

Test material: the first section of *Die Verwandlung* by Franz Kafka, a LibriVox recording (`Abschnitt 1`, 41:06 min, MP3).

Planned end result: one audio file = teacher briefing in English, followed by the first ~10 minutes of the audiobook.

## 2. Setup

### Hardware

* Apple M1, 16 GB unified memory.
* **Constraint:** only one AI model may be loaded at a time. Every step unloads its model before the next step starts. Running models in parallel is not possible on this machine.

### Tools

| Tool | Role in the POC | Notes |
|---|---|---|
| ffmpeg (Homebrew) | Cutting, silence detection, format conversion, loudness normalisation, joining | Already installed |
| [Voicebox](https://github.com/jamiepine/voicebox) desktop app | Speech-to-text (Whisper) and text-to-speech | Local REST API at `http://127.0.0.1:17493`, OpenAPI at `/docs`. MLX backend on Apple Silicon |
| Ollama | Writing the teacher notes | `http://localhost:11434`, only model installed: `qwen3:8b` |
| Python 3.9 (system) | Glue scripts | Standard library only |

### Voicebox models

| Model | Status | Used for |
|---|---|---|
| `whisper-large` (openai/whisper-large-v3, ~8.8 GB) | Already downloaded | German transcription |
| `qwen-tts-1.7B` (Qwen3-TTS Base) | Already downloaded | Not used: cloning only, needs a voice sample |
| `chatterbox-turbo` | Already downloaded | Not used: English only, cloning based |
| `qwen-custom-voice-1.7B` (Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice, ~3.8 GB) | **Downloaded during the POC** via `POST /models/download` | Teacher voice with preset (non-cloned) human voices |

Qwen CustomVoice was chosen because the owner wanted a normal human voice, not a cloned one, and because it is multilingual (German words), accepts natural-language delivery instructions, and offers preset voices. Its presets: Vivian, Serena, Uncle Fu, Dylan, Eric (listed as Chinese), Ryan, Aiden (English), Ono Anna (Japanese), Sohee (Korean). Any preset can be asked to speak any supported language.

## 3. Pipeline and results, step by step

All outputs are numbered and stored in `~/personal/verwandlung voices/`, so each step can be inspected, edited and rerun on its own. Editing a step's output only requires rerunning the steps after it.

| # | Step | Tool | Output |
|---|---|---|---|
| 1 | Cut ~10 minutes at a natural pause | ffmpeg | `01_excerpt.mp3` |
| 2 | Transcribe German | Voicebox / Whisper Large | `02_transcript.txt` |
| 3 | Write teacher notes | Ollama / qwen3:8b | `03_prompt_v*.txt`, `03_teacher_notes_*.txt` |
| 4 | Generate teacher voice | Voicebox / Qwen CustomVoice (Ryan) | `04_teacher_voice.wav` |
| 5 | Join and level | ffmpeg | `05_final.mp3` |

### Step 1 — Cut the excerpt

* Searched for pauses between 9:00 and 11:00 with `silencedetect` (threshold −35 dB, minimum 0.6 s).
* Longest pause near 10:00: 10:01.40–10:02.53 (1.13 s). Cut in the middle at **10:02.0**, with a 0.4 s fade-out.
* Result: `01_excerpt.mp3`, 602.0 s, ~113 kbit/s, 8.5 MB. The original file is untouched.
* **Lesson:** cutting at a fixed time would split a word; cutting at a detected pause is easy and sounds natural.

### Step 2 — Transcription

* **First attempt failed:** Voicebox `/transcribe` rejected the MP3 with `{"detail":"could not open/decode file"}`.
* **Fix:** convert to 16 kHz mono WAV (Whisper's native format, 19.3 MB) and send that with `language=de`, `model=large`.
* Time: **5 min 17 s** for 10 minutes of audio (about 0.5× real time).
* Result: 1,504 words of German. Quality is very good. Small errors found while reading:
  * `Nahheiten … vergaße` instead of `Narrheiten … vergäße`
  * `zappelten Beine` instead of `zappelnden Beine`
  * `Bogenförmigen` capitalised
* The LibriVox announcement at the start is included in the transcript.
* **Limitation:** Voicebox returns only `{"text", "duration"}`, with **no timestamps**. An SRT file cannot be produced from this endpoint. Subinjector's existing pipeline needs timed cues, so a future audio pipeline needs a Whisper setup that returns segment or word timestamps.
* Whisper was unloaded afterwards (`POST /models/whisper-large/unload`). Voicebox had loaded the newly downloaded TTS model on its own, so it was unloaded too before Ollama ran.

### Step 3 — Teacher notes (three attempts)

Ollama settings for all attempts: `qwen3:8b`, `temperature 0.4`, `keep_alive 0` (unload immediately after the call), `stream false`. The prompt contained the instructions followed by the full transcript (~3,300 prompt tokens).

The prompt asked for a briefing **written for listening**: plain spoken English, no markdown, 170–230 words (~90 s), 5–7 items, at least two grammar points that really occur in the text, each German word set apart with a colon and a full stop ("The first word is: Ungeziefer. It means vermin."), German words never inside long English sentences. Full prompt text: `03_prompt_v1.txt` and `03_prompt_v2.txt`.

| Version | Change | Time | Result |
|---|---|---|---|
| v1 | `think: false`, `num_ctx 8192` | 64 s, 233 output tokens | 155 words. All items from the first paragraph. *panzerartig* translated as "like a tank". Grammar points generic, no examples. Asterisks around the book title |
| v2 | Stricter prompt: spread items across the whole transcript, use dictionary forms, translate precisely, quote a real phrase for each grammar point, no asterisks/quotes | ~1 min | 206 words. "Tank" fixed (now "shell or armor"). Still first paragraph only, still inflected forms (*gewölbten*). **Both grammar examples wrong:** *Es war kein Traum* labelled Konjunktiv II (it is indicative Präteritum); *Regentropfen auf das Fensterblech aufschlagen* labelled Präteritum of a strong verb (it is an infinitive) |
| v3 | Same prompt, `think: true`, `num_ctx 16384` | 2 min 30 s, 1,005 output tokens incl. reasoning | 217 words. Better spread (*zappelten*, *Krankenkassenarzt*), but *Schmerz* is too easy for B1. Ignored formatting rules (`**bold**`, `*italics*`). **Both grammar examples wrong again:** *würde den Eltern abzuzahlen* does not exist in the text; *verwandelt* labelled Präteritum of a strong verb (it is the participle of a weak verb). Reasoning saved in `03_teacher_notes_v3_thinking.txt` |

Because the audio should not teach wrong grammar, a **manually corrected version** (`03_teacher_notes_final.txt`, 244 words) was written and used for the voice. It keeps v3's structure and good vocabulary, uses dictionary forms, replaces *Schmerz* with *sich krank melden*, and uses real, correctly labelled examples found by searching the transcript:

* Konjunktiv II: *Wie wäre es, wenn ich noch ein wenig weiterschliefe* (the transcript also contains many *wäre / hätte / würde* forms)
* Strong-verb Präteritum: *Er lag* (liegen), *fand* (finden), *sah* (sehen)

**Important for interpreting the result:** the final audio tests *"is a good teacher briefing worth listening to?"*, not *"can the local setup write a correct briefing on its own?"*. The answer to the second question is currently **no** for `qwen3:8b` with a free-text prompt.

### Step 4 — Teacher voice

* Created Voicebox profile **German Teacher**: `voice_type preset`, engine `qwen_custom_voice`, preset `Ryan`, language `en`.
* Generation request: engine `qwen_custom_voice`, model size `1.7B`, `seed 42`, `language en`, instruct: *"A warm, patient language teacher speaking clearly and a little slowly, with short pauses before each German word."*
* `/generate` is asynchronous: it returns an id and status (`loading_model` → `generating` → `completed`). It gives **no progress percentage or time estimate**. The script polled `GET /history/{id}` and then downloaded `GET /audio/{id}`.
* Result: `04_teacher_voice.wav`, **143 s** (24 kHz mono), about **12 minutes** of generation. It is much longer than the planned 90 s because "a little slowly" slowed the whole text.

### Step 5 — Join

* Each input normalised separately with `loudnorm=I=-16:TP=-1.5:LRA=11`, resampled to 44.1 kHz mono, then joined: teacher voice → 2 s silence → excerpt.
* Loudness before: teacher −19.6 LUFS, audiobook −24.9 LUFS (the narrator was ~5 dB quieter). After: teacher part −17.5 LUFS, book part −17.0 LUFS.
* Result: `05_final.mp3`, **12:27** (747 s).
* `caffeinate` kept the Mac awake while the owner was away and was stopped afterwards.

### Owner's verdict on the result

* The concept "looks good".
* The **voice and tone of the teacher can be better.**
* **German words spoken inside English speech are mispronounced** and need a solution.

## 4. Pronunciation experiments

### 4.1 Single clips (`06_pronunciation_test/`)

A second profile, **German Word Voice** (preset `Serena`, language `de`), was created. Clips were generated one at a time with the same model. Instruct for English: *"A warm, friendly language teacher speaking clearly at a natural pace."* Instruct for German: *"Clear, natural standard German pronunciation."*

| Clip | Content | Length | Owner's verdict |
|---|---|---|---|
| `1_baseline_ryan_en_mixed.wav` | Ryan, `language en`, German words inside English | 15.3 s | Baseline (German words anglicised) |
| `2_ryan_de_words.wav` | Ryan, `language de`: *Ungeziefer. Panzerartig. Krankenkassenarzt. Wie wäre es, wenn ich noch ein wenig weiterschliefe?* | 6.6 s | **Sounds good** |
| `3_serena_de_words.wav` | Serena, `language de`, same text | 6.6 s | **Really good** |
| `4_stitched_ryan_en_de_en.wav` | English Ryan → German Ryan saying only "Ungeziefer." → English Ryan, joined with short pauses | 17.7 s | **Awful and funny: in the middle of the sentence it screams "Ungeziefer"** |

Technical notes:

* Clips 2 and 3 have identical length but different audio (different checksums); the matching length is a coincidence.
* ffmpeg's `loudnorm` silently upsamples to **192 kHz** unless followed by `aresample`; the first stitched file was 6.7 MB instead of ~0.8 MB. Fixed with `aresample=24000`.
* An automatic check (transcribe the German clips with Whisper and compare with the input) **could not run**: after the TTS model had run, every `/transcribe` call failed with `There is no Stream(gpu, N) in current thread`, even after unloading Whisper. This looks like a Voicebox/MLX threading bug when switching models in one session; restarting Voicebox is the likely fix.

**Conclusions:**

1. With `language de`, Qwen CustomVoice pronounces German well with both an English preset (Ryan) and another preset (Serena). No extra model and no narrator clips are needed for pronunciation.
2. **A single word generated on its own has no context, so the model guesses its prosody** (here: shouting). Splicing such a clip into the middle of a sentence sounds broken.

### 4.2 Course-style test (`07_course_style_test/`)

Design changes based on 4.1:

* Never split inside a sentence. English blocks are complete sentences; German comes as its own block, like a language-course recording.
* The German block contains the word **plus the real sentence from the book**, so the model gets context (calmer prosody) and the learner hears the word in context.
* Two voices: Ryan for English, Serena for German. A voice change at a clear pause sounds deliberate.
* Each block is loudness-normalised on its own before joining; 0.7 s pause around German blocks, 0.3 s between English blocks.
* Both example sentences were checked to appear word for word in the transcript before generation.

| Block | Voice | Text | Length |
|---|---|---|---|
| 01_en | Ryan | Okay, let's look at two words before you start listening. The first one appears in the very first sentence of the story. Here it is. | 9.7 s |
| 02_de | Serena | Ungeziefer. Als Gregor Samsa eines Morgens aus unruhigen Träumen erwachte, fand er sich in seinem Bett zu einem ungeheuren Ungeziefer verwandelt. | 11.6 s |
| 03_en | Ryan | That word means vermin, something like a disgusting insect. So right from the start, you know what Gregor has become. The second word is a long one. Gregor is afraid his boss will show up with this person. | 17.6 s |
| 04_de | Serena | Krankenkassenarzt. Gewiss würde der Chef mit dem Krankenkassenarzt kommen. | 5.3 s |
| 05_en | Ryan | That means the health insurance doctor. Long nouns like this are just smaller words glued together, so don't let them scare you. Alright, enjoy the story. | 11.6 s |

Instructs: English *"A warm, friendly language teacher talking to one student, relaxed, natural pace."*; German *"Calm, clear standard German at a moderate, even volume, like an audiobook narrator."*

Result file: `07_course_style_test/course_style_two_words.wav`, 58.9 s (24 kHz mono). Generation of all five blocks took a few minutes; the model was unloaded afterwards. Raw and levelled blocks are in `07_course_style_test/segments/`, so a single block can be regenerated. **Owner's verdict: pending.**

## 5. Findings

### What works

* **Transcription:** Whisper Large gives accurate German without tuning.
* **Word selection by the LLM:** `qwen3:8b` chose useful, relevant words (*Ungeziefer*, *zappeln*, *Krankenkassenarzt*) and mostly translated them well.
* **German pronunciation:** Qwen CustomVoice presets with `language de` sound good.
* **Numbered file per step:** made it easy to inspect, fix and rerun only the later steps.
* **Loudness matching per part** removes the volume jump between teacher and narrator.

### What does not work (yet)

* **Grammar facts from a small LLM:** wrong in all three attempts, including with thinking enabled. Prompt hardening improved style, not correctness.
* **Instruction following on format:** v3 ignored "no markdown"; text-to-speech would read or stumble over symbols.
* **Coverage:** with ~3,300 tokens of dense Kafka in one prompt, the model concentrated on the first paragraph.
* **Delivery instruction scope:** "speak a little slowly" slowed the whole text (143 s instead of ~90 s) and made the tone flat.
* **German words inside English sentences** are anglicised by an English voice.
* **Single-word TTS clips** get unpredictable prosody; splicing them mid-sentence sounds broken.

### Operational findings

| Item | Measurement |
|---|---|
| Transcription, Whisper Large via Voicebox, M1 | ~5.3 min per 10 min of audio |
| Teacher notes, qwen3:8b, no thinking | ~1 min per 10 min section |
| Teacher notes, qwen3:8b, thinking | ~2.5 min per 10 min section |
| TTS, Qwen CustomVoice 1.7B, M1 | ~12 min for 143 s of speech (~5× slower than real time) |
| Total for one 10-minute section | ~20 minutes, mostly transcription and TTS |

* Voicebox could not decode MP3 in `/transcribe`; WAV works.
* Voicebox `/transcribe` returns no timestamps.
* Voicebox `/generate` gives no progress or time estimate.
* Voicebox `/transcribe` failed with an MLX thread error after TTS had run in the same session.
* Voicebox may load a model automatically after it is downloaded; check `/models/status` before starting another model.
* `loudnorm` upsamples to 192 kHz unless resampled explicitly.

## 6. Design discussion and decisions

### Who chooses the words: code or AI?

The first proposal was to let code detect candidate words and grammar. **The owner disagreed: choosing what matters for a B1 learner is exactly what an AI is good at.** Agreed refinement:

* **The AI chooses** the words, grammar points and idioms.
* **Code only checks** facts that need no language knowledge. Example: whether a quoted example appears word for word in the transcript (like Ctrl+F). Run on the POC output:

  | Quote from the LLM | In transcript? | Label correct? |
  |---|---|---|
  | *würde den Eltern abzuzahlen* | **not found** (invented) | — |
  | *Regentropfen auf das Fensterblech aufschlagen* | found | no |
  | *Es war kein Traum* | found | no |

  The check catches invented quotes, but not real quotes with wrong labels.
* **Wrong grammar labels** need a second, narrow AI call ("Is *verwandelt* in this sentence a Präteritum form of a strong verb? Answer yes or no."), a tool, or a stronger model. Small models are more reliable at narrow yes/no questions than at free writing.

### Is prompt hardening enough?

| Problem | Fixable by prompt? | Reliable fix |
|---|---|---|
| Markdown / symbols | Sometimes | Strip them in code before TTS |
| Only first paragraph | Somewhat | Shorter sections (2–3 min), ask for 1–2 items per section |
| Inflected forms | Somewhat | Separate field for the dictionary form in structured output |
| Wrong grammar labels | **Not reliably** | Verification call, dictionary tool, or stronger model |

### Shorter sections

Agreed to test: shorter sections (2–3 min of audio, ~300–500 words) should improve coverage and focus. They may help less with grammar labels, which is a knowledge problem rather than an attention problem.

### Structured output vs natural speech

The owner's concern: structured output like `{"Ungeziefer": "vermin"}` read key by key would not sound like a person explaining. Proposed solution: one LLM answer with two parts:

* `items[]`: structured data (type, word as in text, dictionary form, quote, meaning, why it matters). Code and tools validate this part. It can also feed exports such as Anki.
* `script[]`: the natural spoken explanation written by the LLM as a list of segments, each tagged `en` or `de`. English segments are complete, flowing sentences; German segments are the word plus a book quote.

This is the course-style format from 4.2.

### Tools and RAG (later)

* `find_in_transcript(phrase)`: the LLM checks its own quote before using it.
* `lookup_word(word)`: dictionary lookup (for example from local Wiktionary data) returning dictionary form, word type, and whether a verb is strong. This fills the 8B model's knowledge gap.
* RAG over the learner's earlier glossaries, so the teacher does not repeat known words; RAG over a short grammar reference.
* `qwen3:8b` supports tool calling in Ollama.

### Pronunciation options considered

| Option | Description | Status |
|---|---|---|
| A | Same voice; English segments in `en`, German in `de` | German pronunciation good; splicing single words mid-sentence fails |
| B | English teacher voice + separate German voice for examples | **Being tested** (course style, 4.2) |
| C | Cut the narrator's own pronunciation from the audiobook using word timestamps | Not needed for pronunciation quality; still attractive for learning (exact sound the learner will hear). Needs timestamps |

Assembly complexity for B was judged acceptable: the script is a list of segments, each is one TTS call, ffmpeg joins them with pauses (~30 lines of Python). Both voices use the same model, so the one-model constraint holds.

## 7. Should we build it?

**Recommendation: yes, but not as Subinjector code yet.** Validate first with lab scripts, then build the shared core (the briefing) before anything audio-specific.

**For:**

* Every step of the pipeline works in principle.
* It matches the planned architecture (Roadmap Phase 6: transcription job → enrichment job → synthesis job).

**Against / risks:**

* The briefing has been listened to once. A listening tool is only worth building if it is used repeatedly.
* Voice is the most expensive part (TTS time, assembly, voices). A **text** briefing before listening may give most of the value at a fraction of the cost. Untested.
* The subtitle MVP is unfinished (exports missing), and [the product overview](product.md) lists audiobook processing as an MVP non-goal. Two half-finished pipelines are a real risk.

**Key insight:** the valuable new element is the **briefing** ("before this section, watch out for these things"), not the audio. A briefing also helps subtitle users, for example before watching an episode. Building it first benefits both paths:

```
timed text (from SRT or from Whisper) → sections → briefing (structured, validated) → output
                                                                   ├─ text / JSON / Anki
                                                                   └─ voice (optional, later)
```

## 8. Proposed architecture and tech stack

| Concern | POC | Proposal | Reason |
|---|---|---|---|
| Orchestration, jobs, API, storage | Shell + Python | **Keep Kotlin / Spring Boot / PostgreSQL** | Portfolio goal; durable job system already exists |
| Prompt and voice experiments | Ad-hoc scripts | **Small Python lab script outside the app** | Fast iteration; only proven steps move into Subinjector |
| LLM | qwen3:8b, free text | Same port ([ADR-003](adr/ADR-003-ai-provider-port-and-adapters.md)); **structured JSON + validation**; shorter sections; measure `gemma3:12b` and optionally a cloud model | AI output is untrusted; grammar accuracy is the weak spot |
| Speech-to-text | Voicebox `/transcribe` | **Dedicated Whisper server with timestamps** (whisper.cpp server or mlx-whisper) behind a `TranscriptionProvider` port | Timestamps are required; Voicebox issues (MP3, MLX thread bug) |
| Text-to-speech | Voicebox `/generate` | **Voicebox behind a `SpeechProvider` port** | Works well; port keeps Kokoro or a cloud provider possible |
| Audio processing | ffmpeg | ffmpeg called from the app | Reliable, already used |
| Deployment | Laptop | App and PostgreSQL may run on the Pi; **models run on the Mac or a cloud API** | [ADR-007](adr/ADR-007-local-model-sizing.md): the Pi cannot run these models usefully |
| Audio files | Local folder | Files on disk/object storage, paths in the database | Large binaries do not belong in PostgreSQL |

**Scheduling rule from the one-model constraint:** process a whole book **stage by stage** (transcribe all sections → brief all sections → synthesise all sections), not section by section. Otherwise each section loads and unloads three models.

**Data model sketch** (matches the roadmap): `AudioDocument` alongside `SubtitleDocument`; transcription produces timed segments stored like cues so the existing enrichment pipeline can be reused; `Section` groups cues or segments; `Briefing` holds validated `items[]` and `script[]`; synthesis output is stored per section with a cache key of text + voice + settings, so unchanged segments are not regenerated.

## 9. Task list

Each item follows the project workflow: decision (ADR only if real), small spec, one slice, verification, owner review, PR.

### Phase 0 — Validate (lab scripts, ~1 week, no Subinjector code)

- [ ] 0.1 Listen to the course-style test (`07_course_style_test/course_style_two_words.wav`) and decide whether the format works.
- [ ] 0.2 Combine the POC scripts into one lab script with numbered outputs and a config file (level, section length, voices, instructs).
- [ ] 0.3 Structured output from the LLM: `items[]` (type, form in text, dictionary form, quote, meaning, why it matters) and `script[]` (segments tagged `en`/`de`), using Ollama's JSON schema format. Add the quote check and retry on failure; strip markup before TTS.
- [ ] 0.4 Compare section lengths (2–3 min vs 10 min) and models (`qwen3:8b` vs `gemma3:12b`, optionally a cloud model). Score by counting wrong or invented items per section.
- [ ] 0.5 Try a grammar verification call (narrow yes/no question per grammar item) and measure how many wrong labels it catches.
- [ ] 0.6 Improve teacher tone: test Qwen VoiceDesign (voice from a written description), spoken-style script wording, and instructs without "slowly". Compare 2–3 voices on the same 30-second clip.
- [ ] 0.7 Restart Voicebox and run the Whisper-based pronunciation check on the German clips (objective score next to the listening verdict).
- [ ] 0.8 **Real use:** generate briefings for all of chapter 1 (~12 sections of ~3 min) and listen over several days. Questions: did I listen to all of them? Did the briefing help me catch the words? Is voice better than reading the notes?
- [ ] **Go / no-go decision**, recorded as an ADR that updates the product scope.

### Phase 1 — Text briefing in Subinjector (useful for subtitles immediately)

- [ ] 1.1 ADR: introduce the briefing concept; move audiobooks into scope if Phase 0 says go.
- [ ] 1.2 Spec + slice: group cues into sections (contextual cue grouping is already planned in the README).
- [ ] 1.3 Spec + slice: generate a briefing per section with structured output and validation (quote exists in section text, allowed item types, dictionary form present).
- [ ] 1.4 Spec + slice: grammar verification step, with the measured error rate documented.
- [ ] 1.5 Spec + slice: endpoint to fetch the briefing as JSON/text; later reuse `items[]` for CSV/Anki export (Roadmap Phase 3).

### Phase 2 — Audio input

- [ ] 2.1 ADR: `TranscriptionProvider` port and choice of Whisper server (timestamps required).
- [ ] 2.2 Spec + slice: audio upload and file storage with size and duration limits.
- [ ] 2.3 Spec + slice: transcription job producing timed segments stored like cues, feeding the existing pipeline.
- [ ] 2.4 Spec + slice: split sections at pauses and sentence ends (silence detection + timestamps).

### Phase 3 — Voice

- [ ] 3.1 ADR: `SpeechProvider` port; Voicebox adapter; voice configuration (English teacher voice, German example voice).
- [ ] 3.2 Spec + slice: synthesis job that generates `script[]` segments with caching and per-segment loudness normalisation.
- [ ] 3.3 Spec + slice: assembly of briefing + section audio into an output package; consider one `.m4b` with chapter markers so any audiobook player can use it.
- [ ] 3.4 Spec + slice: stage-by-stage scheduling and explicit model unloading.

### Later

- [ ] RAG on the learner's known words (skip already learned items).
- [ ] `lookup_word` dictionary tool for grammar facts; `find_in_transcript` tool.
- [ ] Narrator pronunciation clips using word timestamps (option C).
- [ ] Cloud LLM option for higher accuracy (no silent fallback, per ADR-003).
- [ ] Reconsider roadmap order: Phase 1 here is useful even if voice is dropped and could come before multi-user and sharing.

## 10. Open questions

* Is a spoken briefing clearly better than a written one for this learner? (Phase 0.8)
* What section length works best for the learner, and for the LLM?
* How many items per section before the briefing becomes too long to remember?
* Is `gemma3:12b` accurate enough on grammar, or is a verification step or cloud model required?
* Which teacher voice and tone does the learner want to hear every day?
* Should the German example use the TTS voice or the narrator's own audio?
* Can the TTS speed on an M1 (~5× slower than real time) be accepted for whole books, or does synthesis need a faster engine or a cloud provider?

## 11. Appendix: artifacts, commands, prompts

### Files (`~/personal/verwandlung voices/`)

| File | Content |
|---|---|
| `verwandlung_01_kafka.mp3` | Original audiobook section (unchanged) |
| `01_excerpt.mp3` | First 10:02 |
| `02_transcript.txt` | German transcript, one sentence per line |
| `03_prompt_v1.txt`, `03_prompt_v2.txt` | Full prompts including transcript |
| `03_teacher_notes_v1.txt`, `_v2.txt`, `_v3.txt` | Raw LLM outputs |
| `03_teacher_notes_v3_thinking.txt` | qwen3 reasoning for v3 |
| `03_teacher_notes_final.txt` | Manually corrected notes used for the voice |
| `04_teacher_voice.wav` | Teacher voice (Ryan), 143 s |
| `05_final.mp3` | Final POC audio, 12:27 |
| `06_pronunciation_test/` | Pronunciation clips (section 4.1) |
| `07_course_style_test/` | Course-style test, final file and individual segments (section 4.2) |
| `lab_scripts/pron_test.py`, `lab_scripts/course_test.py` | Generation scripts for sections 4.1 and 4.2 |

Voicebox profiles created: **German Teacher** (preset Ryan, `qwen_custom_voice`) and **German Word Voice** (preset Serena, `qwen_custom_voice`).

### Commands

Find pauses near the 10-minute mark:

```bash
ffmpeg -ss 540 -t 120 -i verwandlung_01_kafka.mp3 -af silencedetect=noise=-35dB:d=0.6 -f null - 2>&1 | grep silence_end
```

Cut with fade-out:

```bash
ffmpeg -i verwandlung_01_kafka.mp3 -t 602.0 -af "afade=t=out:st=601.6:d=0.4" -c:a libmp3lame -q:a 2 01_excerpt.mp3
```

Transcribe (WAV required):

```bash
ffmpeg -i 01_excerpt.mp3 -ar 16000 -ac 1 excerpt16k.wav
curl -X POST http://127.0.0.1:17493/transcribe -F "file=@excerpt16k.wav;type=audio/wav" -F language=de -F model=large
curl -X POST http://127.0.0.1:17493/models/whisper-large/unload
```

Teacher notes:

```bash
# request body: {"model":"qwen3:8b","prompt":"<03_prompt.txt>","stream":false,"think":false,
#                "keep_alive":0,"options":{"num_ctx":8192,"temperature":0.4}}
curl http://localhost:11434/api/generate -d @request.json
```

Voice profile and generation:

```bash
curl -X POST http://127.0.0.1:17493/profiles -H 'Content-Type: application/json' \
  -d '{"name":"German Teacher","language":"en","voice_type":"preset","preset_engine":"qwen_custom_voice","preset_voice_id":"Ryan","default_engine":"qwen_custom_voice"}'
curl -X POST http://127.0.0.1:17493/generate -H 'Content-Type: application/json' \
  -d '{"profile_id":"<id>","text":"...","language":"en","engine":"qwen_custom_voice","model_size":"1.7B","seed":42,"instruct":"..."}'
# poll GET /history/<generation id> until status is completed, then download GET /audio/<generation id>
```

Join with per-part loudness:

```bash
ffmpeg -i 04_teacher_voice.wav -i 01_excerpt.mp3 -filter_complex \
  "[0:a]loudnorm=I=-16:TP=-1.5:LRA=11,aresample=44100,aformat=channel_layouts=mono[t]; \
   anullsrc=r=44100:cl=mono,atrim=0:2[s]; \
   [1:a]loudnorm=I=-16:TP=-1.5:LRA=11,aresample=44100,aformat=channel_layouts=mono[b]; \
   [t][s][b]concat=n=3:v=0:a=1[out]" -map "[out]" -c:a libmp3lame -q:a 2 05_final.mp3
```

### Prompt rules (v2, final form)

```text
You are a warm, encouraging German teacher. Your student is an English speaker at CEFR level B1.
They are about to listen to the first ten minutes of the German audiobook "Die Verwandlung" by Franz Kafka.

Write a short spoken briefing in English that the student will hear right BEFORE listening.

Content:
- One or two sentences of setup, without spoiling more than the passage itself.
- Five to seven key items. Words and grammar a B1 learner probably does NOT know yet but that matter
  for this passage or appear repeatedly. Skip basic words.
- At least two grammar points typical of this text (e.g. Konjunktiv II, Präteritum of strong verbs,
  participle constructions, separable verbs), only if they really appear in the transcript.
- For each word: the German word, its English meaning, and where or why it shows up.
- End with one sentence of encouragement and a listening tip.

Style rules (read aloud by text-to-speech):
- Plain spoken English only. No markdown, bullets, numbering, headings, asterisks, emojis or brackets.
- 170–230 words (~90 seconds). Short sentences. Address the student as "you".
- Every German word or phrase stands on its own, introduced with a colon and followed by a full stop:
  "The first word is: Ungeziefer. It means vermin."
- Each German word only once; never inside a long English sentence.
- Correct German spelling with umlauts. Do not explain pronunciation.
- Ignore the LibriVox announcement.
- Spread the items across the WHOLE transcript.
- Dictionary forms (gewölbt, not gewölbten).
- Translate precisely (Panzer here means armour or shell, not tank).
- For each grammar point, quote one short real phrase from the transcript, then explain it in one sentence.
- Never use asterisks, italics or quotation marks.

Output only the briefing text.

Transcript:
<transcript>
```

### Final teacher notes used for the voice

```text
You are about to listen to the start of The Metamorphosis by Franz Kafka. Gregor Samsa wakes up and
finds that he has turned into a huge insect. Lying in bed, he worries about his job, his boss and his
family. Here are the key words and grammar to listen for.

The first word is: Ungeziefer. It means vermin. It appears in the very first sentence to describe
Gregor's new body.

The next word is: panzerartig. It means armour-like. It describes his hard back.

The next word is: gewölbt. It means arched. It describes the shape of his belly.

The next word is: zappeln. It means to wriggle. His many thin legs wriggle helplessly the whole time.

The next phrase is: sich krank melden. It means to call in sick. Gregor wonders if he should do it.

The next word is: Krankenkassenarzt. It means the health insurance doctor. Gregor imagines his boss
arriving with this doctor.

Now, two grammar points. First, the Konjunktiv two, for wishes and imagined situations. Listen for:
Wie wäre es, wenn ich noch ein wenig weiterschliefe. It means: What if I slept a little longer. You
will hear wäre, hätte and würde again and again.

Second, the simple past of strong verbs. Listen for: Er lag. That is the past of liegen. You will also
hear fand, from finden, and sah, from sehen.

Do not worry about understanding every word. Follow Gregor's thoughts, and listen for these words.
Enjoy it.
```
