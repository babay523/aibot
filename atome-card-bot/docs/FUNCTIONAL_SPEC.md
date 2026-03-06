# Functional Specification

This document describes the product functionality, user interactions, and verification checklist for the Atome Card customer service AI bot.

## 1. Roles

- End User (customer): chats with the bot, receives URLs or status, can report mismatch.
- Operator (admin): manages KB sources, configures intents, reviews feedback, manages global overrides.

## 2. Supported Question Types

### 2.1 Type A: Knowledge Base (KB) URL Answer

Goal: If the user question matches an existing KB article by vector similarity, return the corresponding article URL.

Behavior:
- Bot searches the KB vector store.
- If similarity >= `kb_threshold`, bot replies with the matched article title + URL.
- If similarity < threshold, bot replies with a fallback message (LLM-assisted) suggesting next steps.

Success criteria:
- For common Atome Card help center questions, the returned URL points to the correct Zendesk article.

### 2.2 Type B: Intent-based Handling (Rule Priority)

Goal: If user question matches a configured intent, it takes priority over KB.

Configured intents include:

1) Card application status
- Trigger: intent keyword match (configurable).
- Behavior: ask for required slot(s) (e.g., `application_id`) if missing, then call a handler (simulated in v1) and respond with the status.

2) Failed card transaction
- Trigger: intent keyword match (configurable).
- Behavior: request `transaction_id`, call handler (simulated in v1), return transaction status.

Priority rule:
- Intent match always overrides Overrides/KB.

## 3. Routing Rules (Deterministic)

For every inbound message:

1. If session has pending intent slot collection: continue slot collection.
2. Else match intent by configured keywords (priority order).
3. Else search global overrides by vector similarity.
4. Else search KB by vector similarity.
5. Else fallback response.

## 4. Feedback and Auto-fix Loop (KB only)

### 4.1 User reports mismatch

Trigger:
- Only when the previous assistant reply is a KB answer (`route=kb`).
- UI provides a button: `答案不匹配`.

System actions:
- Record the mismatch event (feedback record) with:
  - original question
  - returned bad URL
  - similarity score and timestamp

### 4.2 System proposes top-3 candidates

System actions:
- Re-run KB vector search on the original question.
- Aggregate results to article-level.
- Return the top 3 candidate articles by similarity.
- Preferably exclude the previously returned bad URL when generating candidates.

User interaction:
- User selects candidate 1/2/3.

### 4.3 Persist global override

System actions:
- Create a global override rule:
  - key: embedding(question)
  - value: chosen URL
  - active=true
- Mark the feedback as resolved/archived.

Result:
- Future semantically similar questions should hit the override layer and return the chosen URL.

## 5. Admin UI Features

### 5.1 KB Sources

- List KB sources (name, url, enabled).
- Add/update sources.
- Manual sync and rebuild a selected source.
- Display sync stats: article count, chunk count, duration.

### 5.2 Intent Config

- List intents.
- Edit:
  - keywords (JSON array)
  - instructions
  - priority
  - enabled
  - required slots

Rule effect:
- After saving config, routing behavior changes immediately (no restart required).

### 5.3 Feedback Center

- List feedback records by status.
- View mismatch detail:
  - user question
  - bad URL
  - timestamp/status

### 5.4 Global Overrides

- List override rules.
- Enable/disable rules.

Note:
- In v1, disabling an override should affect subsequent routing immediately (implementation may require synchronizing DB state into Milvus).

## 6. Non-Functional Requirements

- Determinism: routing decisions are rule-based; LLM output must not decide routing.
- Privacy: do not store secrets in frontend; DeepSeek key must be server-side only.
- Performance:
  - KB sync uses batch embedding.
  - Online queries embed + 1-2 vector searches.
- Maintainability:
  - KB sources and intents are configurable.
  - Feedback loop produces auditable records.

## 7. Verification Checklist (Manual)

### 7.1 Infrastructure

- `docker-compose up -d` starts Milvus and Postgres.
- Backend can connect to:
  - Ollama embedding endpoint
  - Milvus (collections created)
  - Postgres (schema exists)

### 7.2 KB Sync

- In Admin KB page, click `同步并重建`.
- Verify:
  - articles and chunks are created in DB.
  - vectors inserted into Milvus `kb_chunks`.
  - sync stats show non-zero counts.

### 7.3 KB Answer

- Ask an Atome Card-related question (e.g., "What is Atome card?").
- Verify:
  - route is `kb`
  - response contains the correct Zendesk article URL

### 7.4 Intent Answer

- Ask "申请进度".
- Verify:
  - route is `intent`
  - bot asks for required slot (application_id)

### 7.5 Mismatch Feedback -> Override

- After a `kb` response, click `答案不匹配`.
- Verify:
  - system returns 3 candidates
  - select candidate 2
  - system confirms “已修复”

- Ask a semantically similar question again.
- Verify:
  - route becomes `override`
  - returned URL is the chosen URL

### 7.6 Admin Overrides

- Confirm override rule appears.
- Disable it.
- Verify that subsequent similar queries no longer match override and fall back to KB.

## 8. Out of Scope (v1)

- Internationalization (multi-language intent matching) beyond keyword lists.
- Advanced slot extraction (regex/NLP parsing of IDs).
- Incremental KB sync (currently rebuild by source).
- Security hardening (admin auth, rate limiting, audit exports).
