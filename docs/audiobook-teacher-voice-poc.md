# Audiobook teacher-voice proof of concept

* **Date:** 2026-10-17 (full-episode run during the night of 2026-10-17/18; owner feedback and product direction 2026-10-18)
* **Status:** Proof of concept done, including a Persian teacher voice and a complete 41-minute episode with 21 briefings. Owner's verdict: "really good", would use it as a product. Next: feedback from a few friends (0.19) and a local-only improved run (0.15–0.18)
* **Related:** [Roadmap — Phase 6: Audiobook pipeline](../ROADMAP.md), [ADR-003](adr/ADR-003-ai-provider-port-and-adapters.md), [ADR-007](adr/ADR-007-local-model-sizing.md), [Product overview](product.md)

This document records a manual proof of concept that tested whether Subinjector should grow from subtitles into audiobooks with a spoken "teacher" briefing. It covers what was built, every result and failure, the decisions made in discussion, and the proposed task list. No application code was written; everything ran as shell commands and small Python scripts outside this repository.

## Summary

**Result:** a complete, listenable product exists. The first section of *Die Verwandlung* (41 min) now comes with a short Persian teacher briefing before every ~2 minutes: 52.7 minutes in total, 21 chapters, one consistent teacher voice (Serena, Higgs Audio v3) that explains in Persian and reads each German example from the book. It was produced end to end on an M1 with 16 GB, entirely locally, one model at a time, in about 2.5 hours of compute ([section 4.5](#45-full-episode-run-09_full_episode)).

**What the full run proved:**

| Part | Verdict | Evidence |
|---|---|---|
| Voice | **Solved** | Owner: Serena is "excellent, really teacher-like". German blocks re-transcribed by Whisper: 38 of 39 at ≥ 90 % similarity, median 99 % |
| Format | **Solved** | Course style: German word + real book sentence, then Persian explanation; same voice; per-block loudness; chapters per part |
| Transcription | **Works with a check** | Whisper Large looped or lost punctuation on 5 of 21 parts; an automatic check plus split-in-halves repair fixed all 5 |
| Teacher notes, fully automatic | **Good, not yet trustworthy alone** | `gemma3:12b` + review step: about 35 of 41 items correct with natural spoken Persian; 4 clearly wrong (two taught Whisper errors as words) and a few small form errors |
| Teacher notes, after a ~10-minute review | **Ready to use** | 15 small fixes, listed in `03_notes_reviewed/REVIEW_CHANGES.md`. The review was done by Claude (cloud), standing in for a human; the product must not depend on it ([section 6](#local-models-only-can-the-pipeline-correct-itself)) |
| Model choice | **Decided** | `qwen3:8b` writes broken Persian; `aya-expanse:8b` fluent but formal and breaks format; `gemma3:12b` clearly best |

**Recommendation:** build it, in the order of [section 9](#9-task-list): briefing first, then audio input, then voice. Design it with the four safeguards this run showed are necessary: transcript check, quotes snapped to the source text, a same-model review step, and a voice check with retry.

**Owner's verdict on the full episode (2026-10-18):** "really good"; with a good UI the owner would love to use it. Two product rules follow ([section 4.6](#46-owners-verdict-on-the-full-episode-and-product-direction)):

* **Local models only.** No cloud model as corrector in the final product. Of the 15 review fixes, about 10 can be replaced by local data and code (book text instead of the transcript, a lemmatizer and offline dictionary, stricter checks); the remaining meaning and Persian errors need dictionary-grounded explanations and an in-app "wrong / skip" button.
* **Time-neutral notes.** The teacher says what an item means and how it is used here, never "you will hear" or "you heard", so the briefing can play before or after a part. Detail level, items per part and part length are UI settings.

**Licensing and distribution (sections [10](#10-legal-and-licensing) and [11](#11-distribution-showcase-youtube-and-video)):** LibriVox recordings and public-domain texts are free to cut and publish. The text models (Whisper, gemma) allow production use; **Higgs Audio v3 is non-commercial** except for publishing your own content (YouTube, audiobooks) with an acknowledgment, so a product for others needs Boson's licence or another voice engine. Plan: the product runs locally; a showcase of public-domain books and YouTube channels (English-speaking learners first, then Persian) find the first audience. Recommended video format: on-screen text with word cards, one generated illustration per part, and Shorts made from single items.

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
10. [Legal and licensing](#10-legal-and-licensing)
11. [Distribution: showcase, YouTube and video](#11-distribution-showcase-youtube-and-video)
12. [Open questions](#12-open-questions)
13. [Appendix: artifacts, commands, prompts](#13-appendix-artifacts-commands-prompts)

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
* Result: 1,504 words of German. Quality looked very good when read; **correction from the full run (4.5):** an automatic check later found a Whisper loop in this transcript ("nachher" repeated 10 times) that had been missed by eye. Small errors found while reading:
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

Result file: `07_course_style_test/course_style_two_words_ryan_en_serena_de.wav` (originally `course_style_two_words.wav`), 58.9 s (24 kHz mono). Generation of all five blocks took a few minutes; the model was unloaded afterwards. Raw and levelled blocks are in `07_course_style_test/segments/`, so a single block can be regenerated.

**Owner's verdict:**

* The course-style format is **really good**.
* Without "slowly" in the instruct, the delivery is **much better**.
* Switching from a male English voice (Ryan) to a female German voice (Serena) **feels odd**. One voice for both languages is preferred.
* Some teacher sentences are unnecessary filler ("enjoy the story", explaining that Gregor fears his boss will come with the doctor). This is a prompt issue, deferred to the content work; the focus here is the voice.

### 4.3 Single-voice variants

The lab script was changed to take the English and German voice as arguments and to cache blocks per voice (`<block>_<voice>.wav`), so only missing blocks are generated.

| File | Voices | Length | New blocks generated |
|---|---|---|---|
| `course_style_two_words_ryan_en_ryan_de.wav` | Ryan for English and German | 62.1 s | 02_de (14.9 s), 04_de (5.3 s) |
| `course_style_two_words_serena_en_serena_de.wav` | Serena for English and German | 57.0 s | 01_en (9.9 s), 03_en (15.1 s), 05_en (12.0 s) |

Both work technically; the voice choice was then settled by the Persian test (4.4).

### 4.4 Persian teacher (`08_persian_teacher_test/`)

**Question:** can the teacher explain in Persian (Farsi) instead of English, with the same voice also reading the German examples?

**Voicebox cannot do this:** Persian (`fa`) is not in its language list. Arabic support in Chatterbox would read Persian text with Arabic pronunciation. Options considered:

| Option | Persian | Same voice in fa + de (+ en) | Notes |
|---|---|---|---|
| Voicebox engines (Qwen, Chatterbox, Kokoro) | No | — | `fa` not supported |
| **Higgs Audio v3 4B** (`bosonai/higgs-audio-v3-tts-4b`, MLX via `mlx_audio`) | Yes; the owner had tried it before with a good result | **Yes**, by cloning one reference voice into every language | Already set up in `~/personal/tts` (own venv, Python 3.12) |
| Meta MMS-TTS Persian | Yes | No, Persian only | Robotic, single voice |
| Cloud TTS (Azure, ElevenLabs, OpenAI) | Usually good | Varies | Not local, costs money |

**Setup:**

* Higgs Audio v3 clones a voice from a reference clip plus its exact transcript.
* As reference, the **Voicebox preset voices' own clips** were used (`07_course_style_test/segments/01_en_ryan.wav` and `01_en_serena.wav`). This keeps one synthetic teacher across languages without cloning a real person's voice.
* Same five-block course structure as 4.2; Persian blocks written in informal spoken Persian, German blocks unchanged. No vowel marks.
* Generation settings: `temperature 0.7`, `top_p 0.95`, `top_k 50`, `seed 42`, `max_new_tokens 2048`. Model loaded once for both voices; Voicebox had nothing loaded (one model at a time).
* Script: `lab_scripts/persian_test.py`.

Persian blocks:

```text
01_fa: خب، قبل از اینکه گوش دادن رو شروع کنی، بیا دو تا کلمه رو با هم ببینیم. اولی توی همون جمله‌ی اول داستان میاد. گوش کن.
03_fa: این کلمه یعنی حشره‌ی موذی، یه جور جونور چندش‌آور. پس از همون اول می‌فهمی گرگور به چی تبدیل شده. کلمه‌ی دوم یه کم طولانیه. گرگور می‌ترسه رئیسش با این آدم بیاد.
05_fa: یعنی پزشکِ بیمه‌ی درمانی. اسم‌های بلند آلمانی معمولاً از چند تا کلمه‌ی کوچیک‌تر ساخته شدن، پس ازشون نترس. خب، از داستان لذت ببر.
```

**Results:**

| File | Reference voice | Length | Generation time | Owner's verdict |
|---|---|---|---|---|
| `persian_teacher_ryan.wav` | Ryan | 59.9 s | ~5 min | Persian okay |
| `persian_teacher_serena.wav` | Serena | 62.7 s | ~5.5 min | **Excellent, really teacher-like, worth listening to** |

Per block, Higgs needed 30–95 s; Persian blocks were the slowest. Overall it is about 5–6× slower than real time, similar to Qwen CustomVoice in Voicebox.

Owner's observations on the Serena version:

* The voice sounds like a real teacher.
* Switching Persian → German → Persian works well.
* German pronunciation is good.
* The name **Gregor (گرگور) is mispronounced**, by Ryan as well. Persian script does not write short vowels, so the model has to guess.
* **Vowel marks on the name only fix it.** Block 03 was regenerated with the name spelled **گِرِگور** ("ge-re-gor"); everything else unchanged (`persian_teacher_serena_gregor_a_geregor.wav`, 64.2 s; block 03 took 128 s; script `lab_scripts/persian_fix_gregor.py`). Owner's verdict: **Gregor is now pronounced correctly; "very very good".** A second spelling, گْرِگور, was also generated for comparison.
* **Vowel-marking the whole text is not the fix:** the owner tried that before on another Persian text and the result got worse. The rule is therefore: **add vowel marks to foreign names only**, never to the full text.

**Decision from this test:** the preferred teacher is **Serena's voice via Higgs Audio v3, explaining in Persian, with the same voice reading the German examples, and with vowel marks on foreign names only.** Best result so far: `persian_teacher_serena_gregor_a_geregor.wav`.

### 4.5 Full episode run (`09_full_episode/`)

**Goal:** a better test than one minute of audio: the whole first section (41:06), with a short teacher briefing before every ~2 minutes, produced automatically by one pipeline. Owner's constraint: strictly one model at a time.

**Pipeline:** `lab_scripts/episode_pipeline.py` with five stages, each started separately: `split → transcribe → notes → voice → assemble`. Every stage that uses a model first checks that nothing is loaded in Voicebox or Ollama, and unloads its model at the end. Every stage writes files and skips work that already exists, so any stage can be rerun safely.

#### Split

* `silencedetect` (−35 dB, ≥ 0.5 s) over the whole file; for every 120 s target, the longest pause within ±25 s.
* Result: **21 parts of 98–139 s** (last one 69 s). Part 5 ends at 10:02.0, the same cut as the first excerpt.

#### Transcribe

* Each part transcribed separately with Whisper Large (Voicebox), so no timestamps are needed.
* Voicebox first had to be restarted: its transcription still failed with the MLX thread error. The app quit, but its server stayed alive as an orphan process and answered `Broken pipe`; stopping the orphan and starting Voicebox again fixed it.
* **First pass: 5 of 21 parts were broken** — a word repeated 70, 155, 73 and 14 times in a row ("lieber lieber lieber…"), and one part with no punctuation at all. Re-checking the earlier 10-minute transcript showed the same failure there ("nachher" ×10), which had been missed by eye.
* **Fix:** an automatic check (a word repeated ≥ 5 times in a row, or almost no punctuation) and a repair that splits a failed part at the pause nearest its middle and transcribes the halves (up to two levels). All 5 parts passed after repair. Total 6,060 words.
* Time: 23.6 min first pass, 20.4 min repair.

#### Notes

The notes stage asks for structured JSON (Ollama JSON schema): per item `type`, `german` (dictionary form), `quote` (exact text), `explanation_fa` (one or two sentences of spoken Persian). Code validates every item; failed answers are retried up to three times with the reasons.

Problems found and fixed during the run:

| Problem | Fix |
|---|---|
| Exact quote check rejected good items: the model wrote "er fand sich" where the book says "fand er sich" | **Quote snapping:** find the most similar passage of the part (≥ 80 % similar, `difflib`) and use the real book text; prefer the longest near-equal match and trim fragments that spill over a sentence boundary. Invented quotes are still rejected |
| A quote that does not contain the item (qwen: *sich eine passende Ausrede finden* with an unrelated sentence) | Check that a content word of the item appears in the quote (first five letters, also with separable "zu"/"ge" removed) |
| The model copied the style example's ending into almost every explanation | Shorter example and "do not copy its wording" |
| Random sentence fragments and transcription errors chosen as items | Prompt: only dictionary words or fixed expressions; skip words that look like recognition errors; plus the review step |

**First full run with `qwen3:8b`** (kept in `03_notes_qwen3_8b/`): 39 items, but about a third to half of the Persian explanations were wrong or garbled (invented word "پانچره" for *panzerartig*, "فجایع" in a nonsense sentence, wrong meanings), plus invented dictionary forms and a fixation on "sich …" items. Not voiced.

**Model comparison** on parts 1, 3 and 10 (`03_notes_compare/compare.md`, same prompt and checks for all):

| Model | Persian | Format discipline | Verdict |
|---|---|---|---|
| `qwen3:8b` | Often broken, invented words | Good | Not usable for Persian |
| `aya-expanse:8b` | Fluent and mostly correct, but formal written style despite the instruction | Poor: whole paragraphs as quotes, German inside the Persian (part 3 failed) | Second |
| `gemma3:12b` | **Natural spoken Persian, mostly correct meanings** | Good | **Chosen** |

**Review step** (same model, second call): the model first proposes three candidates; the review keeps the best two, writes dictionary forms, drops fragments and transcription errors, and rewrites wrong or unnatural Persian. The result goes through the same code checks; if it fails, the original items are used. This fixed *panzerartig* ("سفت و محکم مثل زره") and removed two bad candidates in the 3-part test.

**Full run with `gemma3:12b` + review:** 41 items for 21 parts in 38 min; 17 of 21 parts valid on the first attempt; review used in every part. Assessment by reading all 41:

* **About 35 good**, for example *um keinen Preis* → "یعنی به هیچ وجه، اصلاً!", *den Kopf verlieren*, *geschehe was wolle*, *die Zugluft*, *nichts im Kopf haben*, *vor Gewissensbissen närrisch werden*.
* **4 clearly wrong:** *die Nahheiten* and *erschütternde* (Whisper errors for *Narrheiten* and *möbelerschütternde Läuten*, taught as words), *unter vier Augen* (garbled phrase), *aus den Augen, aus dem Sinn* (proverb not in the text).
* **Small issues:** wrong articles (*der Hindernis*, *die Versäumnis*), non-dictionary forms (*ausstieß*), *Rücksicht* chosen twice in different forms.

#### Two versions

* **Automatic** (`03_notes/`): exactly the pipeline's output, as the honest measurement.
* **Reviewed** (`03_notes_reviewed/`, `lab_scripts/make_reviewed_notes.py`): 15 explicit fixes (2 items removed, 13 corrected), each with its reason in `REVIEW_CHANGES.md` and in the section file. Quotes are still validated against the transcript by code. 39 items. **The fixes were written by Claude (cloud), not by a person or a local model**; this version shows the quality a review can reach, not what the local pipeline produces alone. See [section 4.6](#46-owners-verdict-on-the-full-episode-and-product-direction) for how to replace it locally.

#### Voice

* Higgs Audio v3 with Serena's reference clip, one block per German example (word + quote) and one per Persian explanation; vowel-marked spellings applied to names.
* Blocks are named by a fingerprint of their text, so both versions share identical blocks and an edit never reuses stale audio. The reviewed version needed only 19 extra blocks.
* **Higgs failure found:** some blocks were about one second of speech followed by silence up to the 25 s limit. A check on every block (silence > 40 % of the block, or far too long or short for its text at 0.092 s per character) now regenerates with another seed. It caught **5 failures in about 100 blocks, all German blocks, all fixed on the second seed.**
* Time: 46 min for the first 82 blocks (11.7 min of teacher audio), 19 min for fixes and the reviewed version.

#### Assemble and checks

* Per part: blocks with 0.5 s after German and 0.9 s after Persian, 1 s before the narrator, then the two minutes of the book. Every block and every narrator part loudness-normalised on its own.
* Outputs: MP3, and M4B with 21 chapters ("Teil 1"–"Teil 21") for audiobook players; `teacher_notes_*.md` with every item and its position in the file.
* **Reviewed: 52.7 min; automatic: 53.4 min** (41.1 min book + ~11.5 min teacher).
* Loudness: teacher blocks −15 to −17 LUFS, narrator −17 LUFS.
* **Objective German check** (`lab_scripts/check_german_blocks.py`): Whisper re-transcribed all 39 German blocks of the reviewed version; **38 of 39 ≥ 90 % similar to the intended text, median 99 %, lowest 82 %** (correct words; Whisper added an invented ending after a quote that stops mid-sentence).

#### Total compute for one 41-minute section (M1, 16 GB, local)

| Stage | Time |
|---|---|
| Transcription (incl. repair) | 44 min |
| Notes, gemma3:12b + review | 38 min |
| Voice (82 blocks + retries) | ~50 min |
| Assemble and checks | ~5 min |
| **Total** | **~2.3 h**, i.e. an overnight job per book section |

### 4.6 Owner's verdict on the full episode and product direction

**Verdict (2026-10-18, after listening):** "it is actually really good". With a final product with a good UI, the owner "would really love to use it".

**What the owner wants from a product:**

| Wish | Effort, given what the pipeline already produces |
|---|---|
| Briefing **before or after** the part, switchable | Order in the assemble step only, if notes are time-neutral (below) |
| **More or less detail** (items per part, explanation length, grammar or not) | Prompt parameters; treated as UI settings, adjustable later |
| **Adjusted to the learner's level** | Level is already a prompt parameter (B1); a change regenerates notes and voice, not transcripts |
| **Transcript visible while listening** | Per part: available now (one transcript per ~2-minute part). Per sentence or word: needs timestamps, which Voicebox's Whisper does not return (whisper.cpp, faster-whisper or forced alignment) |
| **Save words and idioms to a vault** | Every item is already structured: word, real book sentence, Persian explanation, time in the file. Saving is a button; Anki export is easy |
| **Note taking** while listening | UI work; nothing in the pipeline blocks it |

**Product rule 1: local models only.** The final product cannot use Claude, or any cloud model, as a corrector. The reviewed version in 4.5 is therefore a quality target, not the product. How the 15 fixes map to local replacements is analysed in [section 6](#local-models-only-can-the-pipeline-correct-itself).

**Product rule 2: time-neutral notes.** If the teacher never says "you will hear" or "you just heard", but only "*X* means *Y*; here it …", the same briefing works before or after the part, and the position becomes a setting.

* **Check of the existing notes:** none of the 41 automatic explanations refers to the future or the past. Only 2 contain "اینجا" ("here it means …"), which works in either position. The German block (word + book sentence) is neutral by design. The current episode could therefore be reassembled with briefings after each part without regenerating anything.
* **Prompt change (not yet rerun):** both prompts in `episode_pipeline.py` now state it as a rule: "The app may play it before or after the part, so keep it neutral in time: say what the item means and how it is used here, never 'you will hear' or 'you just heard'."

**Next step chosen by the owner:** send the prototype to a few friends and ask whether they would listen to something like this (task 0.19). Advice recorded for that test:

* Send the MP3 (plays everywhere; M4B chapters mainly help in Apple Books).
* The **automatic** version is the honest sample of local-only quality (including ~4 wrong items); the reviewed one slightly oversells it. Better still: send the result of the local-only improved run (0.15–0.18) once it exists.
* Ask them to listen to the first 10–15 minutes, then: Did you keep listening, and would you use it for a book of your choice? Too many, too few or the right number of explanations? Before or after the part?

## 5. Findings

### What works

* **Transcription:** Whisper Large gives accurate German **when checked**: it loops or drops punctuation on roughly a quarter of 2-minute parts, which an automatic check and split-in-halves repair fix (4.5).
* **Word selection by the LLM:** all models chose useful, relevant words; `gemma3:12b` with a review step also writes natural, mostly correct spoken Persian (4.5).
* **Full automation end to end:** 41 minutes of audiobook to a 53-minute episode with 21 briefings, locally, one model at a time, in ~2.3 h (4.5).
* **Code safeguards around AI output:** quote snapping to the source text, item-in-quote check, transcript check, voice silence check with retry; each caught real failures in the run.
* **German pronunciation:** Qwen CustomVoice presets with `language de` sound good.
* **Numbered file per step:** made it easy to inspect, fix and rerun only the later steps.
* **Loudness matching per part** removes the volume jump between teacher and narrator.
* **Course-style blocks:** complete sentences in the teacher's language, German as separate blocks (word + real book sentence), per-block loudness, pauses around German.
* **One synthetic teacher across languages:** Higgs Audio v3 cloning a Voicebox preset clip (Serena) speaks Persian and German convincingly.
* **Persian teacher:** the owner's favourite result of the whole POC.

### What does not work (yet)

* **Grammar facts from a small LLM:** wrong in all three attempts, including with thinking enabled. Prompt hardening improved style, not correctness.
* **Instruction following on format:** v3 ignored "no markdown"; text-to-speech would read or stumble over symbols.
* **Coverage:** with ~3,300 tokens of dense Kafka in one prompt, the model concentrated on the first paragraph.
* **Delivery instruction scope:** "speak a little slowly" slowed the whole text (143 s instead of ~90 s) and made the tone flat.
* **German words inside English sentences** are anglicised by an English voice.
* **Single-word TTS clips** get unpredictable prosody; splicing them mid-sentence sounds broken.
* **Two different voices** for teacher and examples feel odd.
* **Foreign names in plain Persian text** (Gregor) are mispronounced. Fixed by vowel-marking the name only (گِرِگور); vowel-marking the whole text made Persian worse in the owner's earlier experience.
* **Filler in the teacher's text** ("enjoy the story", plot explanations) adds length without value. (Fixed in the full run by the structured format: only word, quote and explanation.)
* **Small local models in Persian:** `qwen3:8b` writes broken Persian; even `gemma3:12b` + review leaves about 10 % clearly wrong items.
* **Transcription errors propagate:** the LLM taught two Whisper mistakes as German words. Using the book's real text (where available) or a German word-list check removes this locally.
* **Higgs occasionally stops speaking** after a second and fills the block with silence (~5 % of blocks, German only); a check with retry is required.

### Operational findings

| Item | Measurement |
|---|---|
| Transcription, Whisper Large via Voicebox, M1 | ~5.3 min per 10 min of audio |
| Teacher notes, qwen3:8b, no thinking | ~1 min per 10 min section |
| Teacher notes, qwen3:8b, thinking | ~2.5 min per 10 min section |
| TTS, Qwen CustomVoice 1.7B, M1 | ~12 min for 143 s of speech (~5× slower than real time) |
| TTS, Higgs Audio v3 4B (MLX), M1 | ~5–5.5 min for 60 s of speech (~5–6× slower than real time) |
| Total for one 10-minute section | ~20 minutes, mostly transcription and TTS |
| Notes, gemma3:12b + review, per 2-minute part | ~1.5–3 min (38 min for 21 parts) |
| TTS, Higgs Audio v3, per block | ~25–50 s (~4× slower than real time) |
| **Full 41-minute section, all stages** | **~2.3 h** |

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

### Local models only: can the pipeline correct itself?

The owner's rule: no cloud model as corrector in the final product. The 15 fixes Claude made to the automatic notes (`REVIEW_CHANGES.md`) were sorted by what could replace them locally. This is an estimate from the fix list, not yet a test.

| Kind of error | Fixes | Local replacement |
|---|---|---|
| Whisper errors taught as words (*die Nahheiten*, *erschütternde*) | 2 | **Use the book's real text** instead of the transcript: *Die Verwandlung* is public domain (Project Gutenberg); in a product, the learner's ebook. Align text to audio per part. Without a text: a German word-list check rejects non-words |
| Wrong article or non-dictionary form (*die Versäumnis*, *der Hindernis*, *ausstieß*, *sich verbissen*, *abhandengekommen war*) | 5 | **Lemmatizer** (e.g. spaCy German model) plus an **offline dictionary** (e.g. a Wiktionary extract) for lemma and gender. Deterministic, no LLM |
| Errors code can catch (the "......" in a quote; *Rücksicht* taught twice in two forms; a proverb that is not in the quote; *sich scheiden* with no reflexive pronoun in the sentence; *auflösen* where the text has the adjective *aufgelöst*) | 5 | **Stricter checks:** clean quote punctuation; compare lemmas, not strings, for already-covered items; every content word of an expression must be in the quote; "sich …" items need sich/mich/dich/uns/euch in the quote; part-of-speech tag of the item in the quote must match |
| Wrong or vague meaning, garbled Persian (*unter vier Augen*, *verhältnismäßig*, *sich einen Narren aus jemandem machen*, *aus geschäftlichen Rücksichten*, partly *abhandenkommen*) | 4–5 | **Dictionary-grounded explanation:** look the item up first and give gemma the meaning, so it only has to say it in Persian. Remaining errors: an in-app "this explanation is wrong / skip" button for the learner |

Some fixes fall into two rows. **Conclusion:** about 10 of 15 fixes become reliable local data and code steps. Meaning and Persian errors will drop with grounding but not reach zero with a 12B model on 16 GB; the product handles the rest through learner feedback, not an editor. This replaces the earlier recommendation of a mandatory human review step.

### Tools and RAG (later)

* `find_in_transcript(phrase)`: the LLM checks its own quote before using it.
* `lookup_word(word)`: dictionary lookup (for example from local Wiktionary data) returning dictionary form, word type, and whether a verb is strong. This fills the 8B model's knowledge gap.
* RAG over the learner's earlier glossaries, so the teacher does not repeat known words; RAG over a short grammar reference.
* `qwen3:8b` supports tool calling in Ollama.

### Pronunciation options considered

| Option | Description | Status |
|---|---|---|
| A | Same voice; English segments in `en`, German in `de` | German pronunciation good; splicing single words mid-sentence fails |
| B | English teacher voice + separate German voice for examples | Format works (4.2), but two different voices feel odd; **one voice for all languages chosen** (4.3, 4.4) |
| C | Cut the narrator's own pronunciation from the audiobook using word timestamps | Not needed for pronunciation quality; still attractive for learning (exact sound the learner will hear). Needs timestamps |

Assembly complexity for B was judged acceptable: the script is a list of segments, each is one TTS call, ffmpeg joins them with pauses (~30 lines of Python). Both voices use the same model, so the one-model constraint holds.

## 7. Should we build it?

**Recommendation after the full-episode run: yes, build it.** The technical risks are retired: a complete episode was produced automatically, locally, with a voice the owner likes and German examples Whisper confirms at a 99 % median. The owner listened and called it "really good" (4.6). Build the shared core (the briefing) first, then audio input, then voice, with the safeguards from 4.5 and the local correction steps from [section 6](#local-models-only-can-the-pipeline-correct-itself) instead of a review by a cloud model.

**For:**

* The whole pipeline works end to end on the target hardware (4.5), within an overnight run per section.
* It matches the planned architecture (Roadmap Phase 6: transcription job → enrichment job → synthesis job), and the lab script already has the stage structure, file-per-stage outputs and caching the product needs.
* Every failure found had a cheap, testable code fix; none required a different architecture.

**Against / risks:**

* The owner liked the full episode; whether others want it is untested (friends' feedback, 0.19), and real use over several days (0.8) is still open.
* Notes are not fully trustworthy yet (~10 % clearly wrong with the best local model). The product must stay local-only, so it needs book text, lemmatizer, dictionary grounding and stricter checks (0.15–0.18), plus learner feedback in the app for what remains.
* Voice is the most expensive part (~50 min per 41 min of audio). A **text** briefing may give much of the value at a fraction of the cost; still untested.
* The subtitle MVP is unfinished (exports missing), and [the product overview](product.md) lists audiobook processing as an MVP non-goal. Two half-finished pipelines are a real risk; the briefing-first order reduces it.

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
| LLM | qwen3:8b free text, then gemma3:12b structured | Same port ([ADR-003](adr/ADR-003-ai-provider-port-and-adapters.md)); **`gemma3:12b`, structured JSON, ~2-minute sections, 3 candidates + same-model review, code validation with quote snapping**; cloud model as an option | Measured in 4.5: best local Persian; AI output is untrusted |
| Correction of notes | Review by Claude (cloud) | **Local only:** book text instead of transcript where available, lemmatizer + offline dictionary, stricter code checks, dictionary-grounded explanations; in-app "wrong / skip" per item; only changed blocks are re-synthesised | ~10 % of automatic items were clearly wrong; owner's rule: no cloud corrector (4.6) |
| Learner settings | Constants in the lab script | **Briefing before/after the part, items per part, detail, part length, level** as user settings | Time-neutral notes make position a pure playback choice (4.6) |
| Speech-to-text | Voicebox `/transcribe` | **Dedicated Whisper server with timestamps** (whisper.cpp server or mlx-whisper) behind a `TranscriptionProvider` port, **with a loop/punctuation check and split-and-retry** | Timestamps are required; Voicebox issues (MP3, MLX thread bug, orphaned server); Whisper Large looped on 5 of 21 parts |
| Text-to-speech | Voicebox `/generate`, then Higgs Audio v3 | **Higgs Audio v3 with a fixed reference clip (Serena), wrapped in a small local Python HTTP service, behind a `SpeechProvider` port** | Only tested local option that speaks Persian and German in one voice; it has no server of its own, unlike Voicebox; the port keeps Voicebox, Kokoro or a cloud provider possible |
| Teacher voice identity | Voicebox presets | **Reference clip + transcript + seed stored as versioned configuration** | Same teacher across sessions and books |
| Audio processing | ffmpeg | ffmpeg called from the app | Reliable, already used |
| Deployment | Laptop | App and PostgreSQL may run on the Pi; **models run on the Mac or a cloud API** | [ADR-007](adr/ADR-007-local-model-sizing.md): the Pi cannot run these models usefully |
| Audio files | Local folder | Files on disk/object storage, paths in the database | Large binaries do not belong in PostgreSQL |

**Scheduling rule from the one-model constraint:** process a whole book **stage by stage** (transcribe all sections → brief all sections → synthesise all sections), not section by section. Otherwise each section loads and unloads three models.

**Data model sketch** (matches the roadmap): `AudioDocument` alongside `SubtitleDocument`; transcription produces timed segments stored like cues so the existing enrichment pipeline can be reused; `Section` groups cues or segments; `Briefing` holds validated `items[]` and `script[]`; synthesis output is stored per section with a cache key of text + voice + settings, so unchanged segments are not regenerated.

## 9. Task list

Each item follows the project workflow: decision (ADR only if real), small spec, one slice, verification, owner review, PR.

### Phase 0 — Validate (lab scripts, ~1 week, no Subinjector code)

- [x] 0.1 Listen to the course-style test and decide whether the format works. **Done:** course style works; one voice for all languages; Persian teacher with Serena via Higgs Audio v3 preferred (sections 4.2–4.4).
- [x] 0.2 Combine the POC scripts into one lab script with numbered outputs. **Done:** `lab_scripts/episode_pipeline.py`, five stages, cached outputs, one model at a time (4.5). A separate config file is still open; settings are constants at the top of the script.
- [x] 0.3 Structured output from the LLM with validation and retry. **Done** (4.5): `items[]` via Ollama JSON schema; quote snapping, item-in-quote check, Latin-letter and Persian checks; the spoken script is built from the items by a template, so there is no filler.
- [x] 0.4 Compare models. **Done** (4.5): `gemma3:12b` chosen over `aya-expanse:8b` and `qwen3:8b`; ~2-minute sections used. A cloud model was not tested.
- [ ] 0.5 Try a grammar verification call (narrow yes/no question per grammar item). Less urgent: with the dictionary-item rule, the model chose almost no grammar items in the full run.
- [x] 0.6 Improve teacher tone. **Done:** natural pace without "slowly"; Serena via Higgs Audio v3 judged really teacher-like (section 4.4). Qwen VoiceDesign remains an option if a different voice is ever wanted.
- [x] 0.7 Whisper-based check of the German voice blocks. **Done** (4.5): 38 of 39 ≥ 90 %, median 99 % (`lab_scripts/check_german_blocks.py`).
- [ ] 0.8 **Real use:** the episode is generated (`09_full_episode/05_output/verwandlung_01_with_teacher_reviewed.m4b`, 21 parts of ~2 min). **Remaining:** listen over several days. Questions: did I listen to all of it? Did the briefing help me catch the words? Is voice better than reading `teacher_notes_reviewed.md`? Are two items per part the right amount?
- [x] 0.9 Persian script from the LLM. **Done** (4.5): the LLM writes each explanation in spoken Persian; `gemma3:12b` + review is natural and mostly correct; the owner should still judge the Persian of the final episode.
- [ ] 0.10 Small local Higgs Audio v3 HTTP service (one endpoint: text + language → WAV; fixed Serena reference clip, transcript and seed; model loaded once, unloaded when idle), so the lab script, and later Subinjector, call it like Voicebox.
- [x] 0.11 Names in Persian blocks. **Done:** per-book spelling list applied before TTS (`PERSIAN_NAME_SPELLINGS`: Gregor → گِرِگور, Grete → گِرِته); the prompt also excludes proper names as items.
- [ ] 0.12 Store the teacher voice definition (reference clip, transcript, seed, settings) in one versioned place. Partly done: constants in the lab script; still to move into a config file.
- [ ] 0.13 ~~Decide how the human review of notes works in the product~~ **Changed (4.6):** no cloud or editorial review in the product. Instead: design the in-app "this item is wrong / skip" feedback, and measure how many of the 15 fixes in `REVIEW_CHANGES.md` the local steps 0.15–0.18 reproduce.
- [ ] 0.14 Shorten quotes that end mid-sentence (e.g. "…imstande war, das"), so the German example always ends naturally.
- [ ] 0.15 Use the book's real text instead of the Whisper transcript where available (Gutenberg for *Die Verwandlung*): align it to the parts, quote from it. Fallback without a text: German word-list check on chosen items.
- [ ] 0.16 Lemmatizer + offline dictionary (e.g. spaCy German + a Wiktionary extract): dictionary form and article by code; covered-items check on lemmas.
- [ ] 0.17 Stricter item-in-quote checks: all content words of an expression, reflexive pronoun for "sich …" items, matching part of speech; strip "......" from quotes.
- [ ] 0.18 Dictionary-grounded explanations: pass the dictionary meaning to gemma, which only writes the Persian. Then rerun notes → voice → assemble for the full episode and compare with `03_notes_reviewed/` (the quality target).
- [ ] 0.19 **Friends' feedback:** send the prototype MP3 (ideally from the 0.18 run) to a few Persian-speaking German learners; ask the three questions in 4.6.
- [x] 0.20 Time-neutral notes. **Done in the prompts** (4.6); the existing notes are already neutral, so before/after only changes the assembly order. Takes effect on the next notes run.
- [ ] 0.21 Ask Boson (contact@boson.ai) to confirm in writing that the showcase and YouTube channels fall under the Creator Use Grant, and what a local end-user app would need (section 10).
- [ ] 0.22 English channel prompt: English explanations for English-speaking B1 learners with gemma3:12b; compare on parts 1, 3 and 10 like the Persian test.
- [ ] 0.23 Sentence translation step (German → English, later Persian) per part, for the translation line in videos.
- [ ] 0.24 Timestamps: Whisper with word timestamps or forced alignment of the book text (shared with the in-app transcript, 0.15).
- [ ] 0.25 Video renderer test (option A + E): one part as a 16:9 text video and one item as a 9:16 Short; text frames via headless Chrome.
- [ ] 0.26 Image test (option B): FLUX.1 [schnell] via `mflux`, one image per part from a gemma-written prompt; measure time per image and judge style consistency.
- [ ] 0.27 Showcase book list: German public-domain books on LibriVox whose authors died more than 70 years ago, grouped by level.
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

- [ ] 3.1 ADR: `SpeechProvider` port; Higgs Audio v3 service adapter first (Voicebox as alternative); teacher voice configuration (reference clip, transcript, seed, teacher language).
- [ ] 3.2 Spec + slice: synthesis job that generates `script[]` segments with caching and per-segment loudness normalisation.
- [ ] 3.3 Spec + slice: assembly of briefing + section audio into an output package; consider one `.m4b` with chapter markers so any audiobook player can use it.
- [ ] 3.4 Spec + slice: stage-by-stage scheduling and explicit model unloading.

### Later

- [ ] RAG on the learner's known words (skip already learned items).
- [ ] `lookup_word` dictionary tool for grammar facts; `find_in_transcript` tool.
- [ ] Narrator pronunciation clips using word timestamps (option C).
- [ ] Cloud LLM option for higher accuracy (no silent fallback, per ADR-003).
- [ ] Learner app (owner's wish list, 4.6): player with the transcript visible (per part first, per sentence once timestamps exist), note taking, a vault for saved words and idioms with Anki export, settings for briefing position, detail, items per part and level.
- [ ] Reconsider roadmap order: Phase 1 here is useful even if voice is dropped and could come before multi-user and sharing.

## 10. Legal and licensing

Recorded 2026-10-18. This is a reading of the licence texts, not legal advice. Higgs Audio v3 terms are from the `LICENSE` file shipped with the model (Hugging Face cache, "Last Updated: July 8, 2026"); the others from `ollama show --license`, model cards and project pages.

### Source material

* **Kafka's text** is public domain in Germany and the EU (author died 1924; protection ends 70 years after death).
* **LibriVox recordings** are dedicated to the public domain by their readers. LibriVox: anyone may use them "however they wish (even to sell them)", including "chop them up, remix them"; no permission or credit required (credit is appreciated). **Cutting the recording in this POC was fine.**
* Caveat: LibriVox checks copyright **for the USA only**. A German book can be on LibriVox and still protected in Germany (e.g. Hesse, died 1962: protected until 2033). **Rule for anything published: only authors who died more than 70 years ago**, and check translators separately. Safe examples: Kafka, Goethe, Schiller, Brothers Grimm, Fontane, Storm, Schnitzler, Zweig.
* Project Gutenberg texts (for task 0.15) are public domain once the Gutenberg header and name are removed.
* Books users bring themselves are their responsibility. The product must not help with infringement: no DRM removal, no marketing like "process your Audible books", processing on the user's machine, no hosting of user uploads.

### Models and tools

| Component | Licence | Production use |
|---|---|---|
| Whisper Large v3 | MIT (code) / Apache 2.0 (weights) | Yes |
| `gemma3:12b` | Gemma Terms of Use | Yes, including commercial; follow Google's prohibited-use policy and pass the terms on if the model itself is distributed |
| `qwen3:8b` | Apache 2.0 | Yes |
| `aya-expanse:8b` | CC BY-NC 4.0 + acceptable use addendum | **No** (non-commercial). Only used in the comparison |
| Qwen3-TTS CustomVoice (Serena, Ryan presets) | Apache 2.0 | Yes; no Persian |
| Voicebox | MIT (reported) | Yes |
| **Higgs Audio v3 4B** | **Boson Higgs TTS 3 Research and Non-Commercial License** | **Depends on the use** (below) |
| ffmpeg | LGPL/GPL | Yes as a separate program; bundling in a distributed app brings obligations |
| Wiktionary data (task 0.16) | CC BY-SA | Attribution; redistributed dictionary data stays CC BY-SA |

### Higgs Audio v3 in detail

| Use | Allowed? |
|---|---|
| Personal use, hobby, research, short-term evaluation (this POC; sending samples to friends) | Yes, free |
| **Creator Use Grant:** publishing and monetising audio you made ("podcasts, videos, audiobooks, social media posts … ad-supported, sponsored, subscription") on channels you own | Yes, free, with a prominent acknowledgment in the audio or near the top of the description: "This audio was created with Boson AI's Higgs Audio — https://www.boson.ai/higgs-audio" |
| A free, non-commercial local app where the user installs Higgs | Probably yes: the licence mentions "permitted non-commercial application[s]"; requires the licence copy, a NOTICE file and "Built with Higgs TTS 3 licensed from Boson AI USA, Inc." |
| Hosted voice generation, an API, or an end-user app that is paid or part of a business ("production use … any use made available to end users outside Your organization") | **No, needs a written commercial licence from Boson** |
| Cloning a real person's voice | Only with explicit, verifiable consent. Serena is a synthetic Qwen preset; whether it was recorded from a real voice actor is unknown and should be checked before going public |
| Public synthetic audio | Must be disclosed where people could be misled; label it "AI voice" |

**Consequences:**

* The text side (Whisper, gemma) is clean for any product. **The voice is the licensing risk.** No permissively licensed local Persian TTS of Higgs quality is known; Qwen3-TTS (Apache 2.0) covers English and German.
* Keep the voice engine behind the planned `SpeechProvider` port, so Higgs can be a user-installed option and can be replaced if the product ever charges money.
* Confirm the showcase/YouTube reading with Boson in writing before launch (contact@boson.ai), since those videos also advertise a product.
* EU AI Act transparency rules (in force since August 2026): label AI-generated audio and video.

## 11. Distribution: showcase, YouTube and video

### Plan (owner, 2026-10-18)

* **The product runs locally** on the user's machine; users process books they own.
* **An online showcase** publishes a few public-domain LibriVox books, prepared for different levels, so people can hear what the tool does.
* **YouTube** to find the first audience: one channel for English-speaking learners of German (**start here: the bigger market**) and one for Persian speakers. Published videos fall under the Higgs Creator Use Grant; for the English channel, Qwen3-TTS (Apache 2.0) with the original Serena voice is a licence-free alternative that speaks English and German. The channel replaces or extends the friends' test (0.19) with real numbers: watch time, retention around teacher parts, and comments like "can I do this with my own book?".
* For the owner's own channels, reviewing a section before upload is normal editing; the local-only rule applies to the product, where nobody reviews.

### YouTube-specific points

* Describe the source (LibriVox reader, book) and the AI voice in the description; tick YouTube's "altered or synthetic content" box when in doubt.
* **Content ID:** other channels upload LibriVox recordings and sometimes claim them. Dispute false claims with the LibriVox page as proof.
* **Monetisation:** YouTube demonetises mass-produced, repetitive "inauthentic content". A plain audiobook over a still image is at risk; teacher parts and on-screen text make the videos clearly more than a re-upload.

### What we already have for a video

| Material | Status |
|---|---|
| Book audio per ~2-minute part, teacher audio blocks, chapter times | Yes |
| German text per part (Whisper; later the book text, 0.15) | Yes, **without sentence or word timing** |
| Teacher items: German word, book sentence, explanation | Yes, explanations in **Persian only** |
| Full translation of the text (English or Persian) | **No.** New step: gemma translates sentence by sentence; for the English channel, explanations also need an English prompt and a quality check (gemma has not been tested in English; qwen3's English grammar notes were wrong) |
| Sentence or word timestamps | **No.** Needed for karaoke-style text; same gap as "transcript while listening". Options: Whisper with word timestamps (whisper.cpp, mlx-whisper), or forced alignment of the book text to the audio |

Local toolchain check: the installed ffmpeg has `libx264`, VideoToolbox, `zoompan` and `xfade`, but **no `libass` and no `drawtext`**, so it cannot draw text itself. Text frames would be rendered as images first (headless Chrome, already installed, handles Persian right-to-left text correctly; or Pillow with `raqm`), or ffmpeg is rebuilt with `libass`.

### Video options

| Option | What the viewer sees | Local cost on M1 16 GB | Licence | Verdict |
|---|---|---|---|---|
| **A. Text video** | German text on screen (current sentence highlighted once timestamps exist), translation line below; during teacher parts a **word card** (word, book sentence, explanation) | Rendering only: minutes per episode | No model | **Core of every video.** Most useful for learners, cheap, and clearly not a plain re-upload |
| **B. A + one illustration per part** | A, over a generated image per ~2-minute part with a slow pan/zoom (`zoompan`) | One image per part; gemma writes the image prompt from the part's text. Speed to measure (FLUX.1 [schnell] in 4 steps via `mflux` is the candidate) | FLUX.1 [schnell]: Apache 2.0. SDXL: OpenRAIL++ (commercial allowed with use restrictions). **FLUX.1 [dev] is non-commercial; avoid** | **Recommended for YouTube.** Same style prompt for every image; keeping Gregor's look consistent across images is the hard part |
| **C. Short generated clips** | A few seconds of moving video per part, or a short intro loop | Very slow on 16 GB (estimated many minutes per few seconds of low-resolution video, if it fits at all); not realistic for 50 minutes of video | Wan 2.1/2.2: Apache 2.0. HunyuanVideo: its licence excludes the EU, UK and South Korea. LTX-Video: check terms | Only for intros or Shorts, if at all |
| **D. Talking teacher avatar** | A generated face lip-syncing the teacher parts | Slow and fragile on M1; some lip-sync tools depend on non-commercial face models | Varies; check each | Later, only if retention data asks for a face |
| **E. Shorts (9:16)** | One item per Short: word card + book sentence + explanation, about 20–30 s, optionally with the part's image | Rendering only | As A/B | **High value:** one section gives ~40 ready Shorts; Shorts are how new channels get found |

**Recommendation:** A as the base, B for long videos, E for reach. Skip C and D until the channel shows people stay.

## 12. Open questions

* Is a spoken briefing clearly better than a written one for this learner? (Phase 0.8)
* What section length works best for the learner, and for the LLM?
* How many items per section before the briefing becomes too long to remember?
* Is `gemma3:12b` accurate enough? Partly answered: ~35 of 41 items good. A cloud model is ruled out as corrector (4.6); how far do local data and checks (0.15–0.18) close the gap?
* Which teacher voice and tone does the learner want to hear every day?
* Should the German example use the TTS voice or the narrator's own audio?
* Can the TTS speed on an M1 (~5× slower than real time) be accepted for whole books, or does synthesis need a faster engine or a cloud provider?
* Can a local model write natural, correct spoken Persian? Answered: `qwen3:8b` no; `gemma3:12b` with a review step mostly yes (4.5).
* Can transcription errors be caught before the LLM teaches them? Proposed: use the book's text where available, else a German word-list check (0.15).
* Do other learners want this? (0.19)
* Briefing before or after the part: which do learners prefer, or should it stay a setting?
* Does the teacher language become a per-user setting (Persian, English, …), and does the briefing prompt need one version per teacher language?
* What licence terms apply to Higgs Audio v3 and to cloning Qwen preset voice output? Mostly answered (section 10): personal use and own channels are fine; a product for others needs Boson's commercial licence. Open: Boson's written confirmation (0.21), and whether the Serena preset is based on a real voice actor.
* Which video format keeps viewers: text only, text + illustrations, or Shorts? (section 11)
* Is gemma3:12b good enough for English explanations and sentence translations? (0.22, 0.23)

## 13. Appendix: artifacts, commands, prompts

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
| `07_course_style_test/` | Course-style tests: mixed voices and single-voice variants, plus cached segments per voice (sections 4.2–4.3) |
| `08_persian_teacher_test/` | Persian teacher with Higgs Audio v3: Ryan and Serena versions, Gregor spelling variants, segments (section 4.4) |
| `09_full_episode/` | Full-episode run (section 4.5): sections, transcripts, notes (automatic, reviewed, qwen, comparison), voice blocks, final MP3/M4B and notes; own `README.md` |
| `lab_scripts/pron_test.py`, `lab_scripts/course_test.py` | Generation scripts for sections 4.1–4.3 (`course_test.py <out> <en_voice> <de_voice>`) |
| `lab_scripts/persian_test.py`, `lab_scripts/persian_fix_gregor.py` | Higgs Audio v3 scripts for section 4.4; run with `~/personal/tts/.venv/bin/python` |
| `lab_scripts/episode_pipeline.py` | Full-episode pipeline: `split`, `transcribe`, `notes`, `voice [notes dir]`, `assemble [notes dir]`, `compare <model>`, `compare_report` |
| `lab_scripts/make_reviewed_notes.py`, `lab_scripts/check_german_blocks.py` | Human-review fixes for the notes; Whisper check of the German voice blocks |

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
