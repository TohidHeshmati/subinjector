# ADR-007: Local model sizing for target hardware

* **Status:** Informational (records findings; does not select a model)
* **Date:** 2026-10-05

## Context

Cue enrichment calls a local Ollama model once per subtitle cue. The configured default, `qwen3:0.6b`, is suitable for smoke tests but too small to write reliable German grammar and idiom explanations as strict JSON. The project needs to run on two machines:

- **Development laptop:** Apple M1, 16 GB unified memory. Ollama uses the GPU through Metal.
- **Raspberry Pi 5:** 8 GB RAM assumed (4 GB and 16 GB variants exist). CPU-only inference; no GPU acceleration in Ollama.

Workload shape per cue, based on the current German prompt ([ADR-004](ADR-004-language-specific-enrichment-prompts.md)):

- **Input:** roughly 400–500 tokens (about 214-word prompt template plus the previous, target, and next cue).
- **Output:** typically 150–250 tokens of JSON, capped at 512 by `maxOutputTokens` ([ADR-003](ADR-003-ai-provider-port-and-adapters.md)).
- **Volume:** a 45-minute episode has roughly 600–900 cues, processed sequentially by one worker.

Per-cue time is therefore *prompt reading time + output tokens ÷ generation speed*. On the Pi, prompt reading is also slow on CPU and adds tens of seconds for 4B-class models.

This record collects model size, memory, speed, and expected quality so later choices start from shared numbers. The figures are approximate; see the evidence notes below.

## Findings

### Model comparison

Memory is approximate resident memory with Q4_K_M quantization (Ollama's default) and a short context. It includes weights, KV cache, and runtime overhead. Leave about 2–3 GB for the OS; on the Pi, also leave room for the app and PostgreSQL.

| Model | Download | Memory in use | M1 16 GB speed | Pi 5 8 GB speed | Pi 5 time per cue | German explanations | Strict JSON |
|---|---|---|---|---|---|---|---|
| `qwen3:0.6b` | ~0.5 GB | ~1 GB | very fast | ~15+ tok/s | ~15–25 s | poor | unreliable |
| `gemma3:270m` | ~0.3 GB | ~0.5 GB | very fast | ~25+ tok/s | ~10 s | very poor | unreliable |
| `gemma3:1b` | ~0.8 GB | ~1.2 GB | very fast | ~8–12 tok/s (measured) | ~20–40 s | weak | fair |
| `qwen2.5:1.5b` | ~1 GB | ~1.5 GB | very fast | ~10 tok/s (measured) | ~20–40 s | weak | fair |
| `qwen3:1.7b` | ~1.4 GB | ~2 GB | very fast | ~7–9 tok/s | ~30–50 s | weak to fair | fair |
| `qwen2.5:3b` | ~1.9 GB | ~2.5 GB | fast | ~5 tok/s (measured) | ~45–75 s | fair | good |
| `qwen3:4b` | ~2.6 GB | ~3.5 GB | fast | ~3–5 tok/s | ~1–1.5 min | fair to good | good |
| `gemma3:4b` | ~3.3 GB | ~4 GB | fast | ~3–5 tok/s | ~1–1.5 min | good | good |
| `qwen3:8b` | ~5 GB | ~6 GB | ~10–15 tok/s, ~15 s/cue | ~1–2 tok/s | ~2.5–4 min | good | very good |
| `gemma3:12b` | ~8 GB | ~9 GB | ~6–9 tok/s, ~25–35 s/cue | does not fit usefully | — | very good | good |
| `gemma3:27b` | ~17 GB | ~18 GB+ | does not fit in 16 GB | does not fit | — | excellent | good |

### Time per episode

At about 750 cues per episode:

| Setup | Time per cue | Time per episode |
|---|---|---|
| Pi 5, `gemma3:1b` | ~30 s | ~6 h |
| Pi 5, `gemma3:4b` | ~1–1.5 min | ~12–19 h |
| M1, `qwen3:8b` | ~15 s | ~3 h |
| M1, `gemma3:12b` | ~30 s | ~6 h |
| Cloud API (e.g. Haiku) | ~1–3 s | minutes |

### Model families

- **Gemma 3 (Google):** sizes 270M, 1B, 4B, 12B, and 27B. Trained on more than 140 languages, so German is a relative strength at each size. The 1B model is text-only with a 32K context; 4B and larger also accept images and have a 128K context, neither of which this project needs. It has no thinking mode. The Ollama adapter always calls `disableThinking()`; whether Ollama ignores that for Gemma or returns an error must be checked in the first smoke test.
- **Qwen3 (Alibaba):** sizes 0.6B, 1.7B, 4B, 8B, 14B, and larger. Strongly multilingual and reliable at following JSON instructions. It has a thinking mode, which this project disables (`spring.ai.ollama.chat.think=false`) to save output tokens.
- **Qwen2.5:** the previous generation. It appears in Pi benchmarks and gives measured reference points, but Qwen3 replaces it at the same sizes.

### What the numbers suggest

- **Quality starts at about 4B parameters.** Below that, expect shallow or wrong German explanations and more cues skipped by output validation. The validator protects data integrity but not usefulness.
- **The Pi can run 4B models, but only as overnight batch work.** 8B models fit in 8 GB but run too slowly to be practical. A 16 GB Pi lets bigger models fit; it does not make them faster.
- **The M1 is the practical local machine.** `qwen3:8b` balances speed and reliability; `gemma3:12b` likely gives better German explanations at about half the speed and needs most free memory.
- **Splitting the system works without code changes.** The Pi can host the app and PostgreSQL while `OLLAMA_BASE_URL` points to Ollama on the Mac (started with `OLLAMA_HOST=0.0.0.0`). Jobs are durable, so pending cues wait while the Mac is asleep.
- **For the Pi long-term, a cloud provider is the realistic option.** Fast, good-quality enrichment for a whole episode on the Pi is what the cloud adapters in roadmap Phase 3 would add. Under ADR-003, that must be an explicit choice, never a silent fallback.

### Evidence and confidence

- **Measured by third parties on a Pi 5:** `gemma3:1b` (about 11.5 tok/s), `qwen2.5:1.5b` (about 10 tok/s), and `qwen2.5:3b` (about 5.2 tok/s), all Q4_K_M.
- **Estimated:** other Pi speeds, scaled from those measurements by parameter count, and all M1 speeds, from typical Apple Silicon results. Not measured on this project's hardware.
- **Quality ratings** are qualitative expectations, not measurements from this project's prompt.
- The project's opt-in smoke tests (`OllamaLocalSmokeTests`, `CueEnricherLocalSmokeTests`) are the way to replace estimates with measured speed, skip rate, and note quality.

Sources:

- [Gemma 3 on Raspberry Pi 5: Benchmarked](https://www.kunalganglani.com/blog/gemma-3-raspberry-pi-5-benchmark.md)
- [Research and analysis of LLM performance on the Raspberry Pi 5](https://stratospherelinuxips.readthedocs.io/en/develop/immune/research_rpi_llm_performance.html)
- [Tiny AI models for Raspberry Pi in 2026](https://dev.to/george_mbaka_62347347417a/tiny-ai-models-for-raspberry-pi-to-run-ai-locally-in-2026-ik1)
- [Run LLMs on Raspberry Pi 5: Setup Guide (2026)](https://toolhalla.ai/blog/run-llms-raspberry-pi-setup-guide-2026)

## Decision

No model is selected. `qwen3:0.6b` stays the configured default for smoke tests, and `OLLAMA_MODEL` selects a model per environment. A model choice should follow a measured comparison on the target hardware.

## Consequences

Later decisions about the default model, deployment topology, or adding a cloud provider can start from these numbers instead of rediscovering them. The table will age as new models are released and must not be treated as current without checking.

## Revisit conditions

- **Measurements:** smoke-test results on the M1 or the Pi; replace the estimates with them.
- **Model releases:** a new model family or size is released for Ollama.
- **Pi hardware:** an accelerator such as the Raspberry Pi AI HAT+ 2 becomes part of the Pi setup.
- **Prompt size:** the prompt or output contract changes the per-cue token counts significantly.
