# Technical Architecture

This document describes the technical architecture for the Atome Card customer service AI bot implementation in this repo.

## 1. Goals

- Route user questions by priority: `Intent` -> `Overrides` -> `Knowledge Base (KB)` -> `Fallback`.
- For KB-type questions, return the matched help center article URL.
- Provide an admin UI to manage KB sources, intent rules, and review feedback/overrides.
- Support a feedback loop: when users report “answer mismatch”, the system proposes top-3 candidates and persists the user-selected URL as a global override.

## 2. Tech Stack

- Backend: Java 17, Spring Boot 3.x, Spring Web + WebFlux, Spring Data JPA
- Frontend: Vue 3, Vue Router, Pinia, Axios
- Relational DB: PostgreSQL 15 (docker)
- Vector DB: Milvus standalone (docker)
- Embeddings: Local Ollama embedding model via HTTP (`/api/embed`)
  - Model configured as: `dengcao/Qwen3-Embedding-8B:Q5_K_M`
- Chat LLM: DeepSeek API (used only for fallback and follow-up phrasing)

## 3. Deployment Topology (Local Dev)

```
┌────────────────────┐          ┌────────────────────┐
│   Vue Frontend     │  HTTP    │   Spring Backend   │
│  :8081 (dev)       ├─────────►│  :8080             │
└────────────────────┘          └─────────┬──────────┘
                                          │
                         ┌────────────────┼─────────────────┐
                         │                │                 │
                         ▼                ▼                 ▼
                 ┌────────────┐   ┌────────────┐   ┌──────────────────┐
                 │ PostgreSQL  │   │   Milvus   │   │      Ollama       │
                 │ :5432       │   │ :19530     │   │ :11434 (local)    │
                 └────────────┘   └────────────┘   └──────────────────┘
                                          │
                                          ▼
                                 ┌──────────────────┐
                                 │ DeepSeek API      │
                                 │ (Internet)        │
                                 └──────────────────┘
```

## 4. Backend Module Architecture

Package root: `backend/src/main/java/com/atome/bot`

- `client/`
  - `OllamaClient`: calls `POST {ollama.base-url}/api/embed` to generate embeddings (batch supported).
  - `DeepSeekClient`: calls DeepSeek chat-completions for fallback answers and follow-up questions.
- `config/`
  - `MilvusConfig`: creates `MilvusClient`.
  - `InitializationConfig`: at startup, probes embedding dimension from Ollama and initializes Milvus collections.
- `service/`
  - `RouterService`: orchestrates routing and persists chat messages.
  - `IntentService`: loads intent rules from DB, keyword matching by priority.
  - `SessionStateService`: stores per-session slot collection state in DB.
  - `ZendeskCrawlerService`: crawls Zendesk help center pages.
  - `Chunker`: splits article text into chunks.
  - `KbSyncService`: manual KB sync (crawl -> chunk -> embed -> store).
  - `MilvusService`: creates collections, inserts/searches vectors.
  - `FeedbackService`: mismatch feedback workflow (top-3 candidates -> persist override).
- `controller/`
  - `ChatController`: `/api/chat`
  - `FeedbackController`: `/api/feedback/*`
  - `AdminController`: `/api/admin/*`

## 5. Data Architecture

### 5.1 Relational Data (PostgreSQL)

Schema is created by `infra/init.sql`.

Main tables:

- `kb_source`: configurable KB entry URLs.
- `kb_article`: crawled article metadata and full body text.
- `kb_chunk`: chunk text per article. (Vector is stored in Milvus.)
- `intent_config`: intent rules (keywords, instructions, handler, required slots).
- `chat_message`: chat transcript + routing metadata.
- `session_state`: per-session slot collection state.
- `feedback`: mismatch reports.
- `feedback_candidate`: top-3 candidate URLs generated during mismatch handling.
- `override_meta`: audit of global override rules.
- `embedding_config`: records embedding model name/dimension (optional consistency check).

### 5.2 Vector Data (Milvus)

Collections:

1) `kb_chunks`
- PK: `chunk_id` (Int64)
- `embedding` (FloatVector, dim=D)
- `article_id` (Int64)
- `source_id` (Int32)

2) `overrides`
- PK: `override_id` (Int64)
- `embedding` (FloatVector, dim=D)
- `active` (Bool)
- `chosen_url` (VarChar)

Indexing (default)
- Index type: HNSW
- Metric: COSINE
- Params: `M=16`, `efConstruction=200`
- Search param: `ef=128`

Dimension (D)
- Determined at application startup via `OllamaClient.probeDim()`.
- Both collections must use the same dimension; if a mismatch is detected, the backend recreates the collections (dev-friendly behavior).

## 6. Key Request Flows

### 6.1 Chat Routing Flow

Input: `{sessionId, message}`

```
message
  ├─► Intent match (DB-configured keywords, priority order)
  │     └─► handler (slot follow-up or simulated status) -> response
  ├─► Overrides search (Milvus overrides, filter active=true)
  │     └─► if score >= override_threshold -> chosen_url
  ├─► KB search (Milvus kb_chunks topK)
  │     ├─► aggregate chunk hits -> article score
  │     └─► if score >= kb_threshold -> article_url
  └─► Fallback (DeepSeek) -> response
```

Output:
- Always includes `route` in `{intent|override|kb|fallback}`.
- If `route=kb`, UI enables mismatch feedback button.

### 6.2 KB Sync Flow (Manual)

Triggered by Admin UI: `POST /api/admin/kb-sync { sourceId, rebuild:true }`

1. Crawl category page -> extract article URLs
2. Fetch each article -> extract title/body/canonical URL
3. Chunk body text
4. Batch embed chunks via Ollama
5. Persist:
   - DB: `kb_article`, `kb_chunk`
   - Milvus: `kb_chunks`

Rebuild strategy:
- For a given `sourceId`, delete previous DB rows and Milvus vectors, then re-ingest.

### 6.3 Mismatch Feedback -> Auto Fix -> Global Override

Precondition: last assistant message is `route=kb`.

1) User clicks “answer mismatch”
- Backend creates a `feedback` record.
- Backend re-runs KB vector search for the original question.
- Backend returns top-3 candidate article URLs sorted by similarity (excluding the previous bad URL if possible).

2) User selects candidate (rank 1/2/3)
- Backend stores `override_meta` in DB.
- Backend embeds the original question and inserts into Milvus `overrides` with `chosen_url`.
- Feedback marked archived.

Effect:
- Future similar queries hit `overrides` first and return the user-confirmed URL (global behavior).

## 7. Configuration

Backend config: `backend/src/main/resources/application.yml`

Important keys:
- `ollama.base-url`
- `ollama.embed-model`
- `milvus.host`, `milvus.port`
- `bot.thresholds.override`, `bot.thresholds.kb`
- `bot.kb-search.chunk-top-k`

Secrets:
- DeepSeek API key must be injected via environment variable `DEEPSEEK_API_KEY`.

## 8. Observability

- Logs: Spring logs for sync progress, routing decisions, and key errors.
- Persisted audit trails:
  - `chat_message` records route/matched URL.
  - `feedback` + `feedback_candidate` capture mismatch events and suggested candidates.
  - `override_meta` records global override rules.

## 9. Current Implementation Notes / Known Gaps

These are important for validation and future hardening:

- Override search score: the current `RouterService` implementation should parse and use the actual Milvus similarity score; it currently does not propagate the returned score. (This affects `override_threshold` behavior.)
- Slot filling: current slot collection is simplified; it should map required slots per intent and parse user inputs (e.g., transaction ID extraction).
- DeepSeek: optional; fallback works best when `DEEPSEEK_API_KEY` is configured.
- CORS: if frontend runs on a different port, backend should enable CORS for local dev.
