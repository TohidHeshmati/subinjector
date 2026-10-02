# Subinjector — Roadmap

Language learning enrichment platform. Feed it subtitle content (and eventually audio), get back learnable material.

---

## Phase 1 — Deployable & safe

- **Docker Compose** — app + Postgres, `docker compose up` on the Pi
- **Input limits** — cap cue count, file size, and jobs per document at submission time
- **Swagger / OpenAPI docs** — `springdoc-openapi`, one dependency

## Phase 2 — Multi-user

- **Authentication** — JWT or session-based, email/password to start
- **User ownership** — documents and jobs belong to a user
- **Configurable worker concurrency** — 1 worker on Pi, 2–4 on Mac; thread pool instead of single `@Scheduled`

## Phase 3 — More value from existing content

- **Processed SRT output** — `GET /api/enrichment-jobs/{id}/results.srt` — SRT with inline notes
- **CSV / TSV export** — one row per enrichment note, importable to any flashcard app
- **Anki `.apkg` export** — proper deck with front/back cards
- **More languages** — Spanish, French, Italian first; each is a prompt file + enum value
- **OpenAI provider** — Spring AI adapter, API-based, zero local compute (good for Pi)
- **Anthropic Claude provider** — same; Haiku is fast and cheap
- **LM Studio provider** — OpenAI-compatible API, same adapter

## Phase 4 — Richer enrichment

- **Translation mode** — translate each cue to English alongside enrichment; separate `EnrichmentType`
- **Difficulty filter** — only annotate vocabulary above the learner's stated CEFR level
- **Enrichment modes** — `VOCABULARY_FOCUS`, `GRAMMAR_FOCUS`, `FULL`
- **Prompt versioning** — track which prompt version produced which enrichment; re-enrich on prompt improvement
- **Per-job provider selection** — choose LLM at job creation time

## Phase 5 — Sharing / social

- **Public/private documents** — visibility flag on `subtitle_document`; default private
- **Share links** — UUID-based read-only URL, no auth required to view
- **Browse public documents** — searchable by language; community can reuse processed content
- **Fork / reuse** — create a new enrichment job from someone else's public document (already works technically, just needs auth + visibility check)
- **Community quality signals** — thumbs up/down on individual enrichment notes

## Phase 6 — Audiobook pipeline (long run)

Upload an audiobook audio file. The pipeline:

1. **Transcription** — Whisper (local via Ollama or API) splits audio into timestamped paragraphs
2. **Paragraph enrichment** — same teacher-note concept as subtitle cues: vocabulary, grammar, idioms per paragraph
3. **Voice synthesis** — generate a teacher voice reading the notes (TTS: Kokoro, ElevenLabs, or similar)
4. **Output package** — paragraph audio + teacher notes audio + written notes, downloadable as a study bundle

This is architecturally very similar to the current enrichment pipeline:
- `AudioDocument` replaces `SubtitleDocument`
- `AudioSegment` replaces `SubtitleCue`
- Transcription job → enrichment job → synthesis job (three sequential async stages)

## UI (long run)

Self-hosted web interface, hostable like Immich:

- Upload SRT or audio file
- Browse your documents and job progress in real time
- Read enrichment results inline
- Export flashcards
- Manage sharing / public links
- Dark mode, clean reader layout optimised for language study

Stack options: Next.js (SSR, good for SEO on public docs), or a lighter SPA (Vue/Svelte) since it's self-hosted.
