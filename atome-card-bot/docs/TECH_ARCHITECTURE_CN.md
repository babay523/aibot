# 技术架构文档（CN）

本项目为 Atome Card 客服 AI 机器人，支持"意图优先 + 向量知识库 + 纠错闭环全局修复"。

## 1. 目标与约束

- 问题处理分两类：
  - 知识库问答：向量相似度检索命中后返回对应 Zendesk 文章 URL
  - 意图问答：命中意图则按附加说明执行（如申请进度/失败交易查询）
- 路由优先级固定且可审计：Intent > Overrides > KB > Fallback
- LLM（DeepSeek）只做：兜底话术/追问话术/润色；不参与路由决策、不生成或猜测 URL
- 纠错闭环：用户反馈"答案不匹配"后，系统返回 Top3 候选链接，用户选定后形成全局 Overrides，后续优先命中

## 2. 技术栈

- 后端：Java 17 + Spring Boot（REST API、抓取、路由、反馈闭环）
- 前端：Vue 3（聊天界面 + 管理后台）
- Embedding：本地 Ollama Embedding 模型（HTTP `/api/embed`）
  - 模型：`bge-m3`（BAAI/bge-m3，跨语言检索，1024 维）
- 向量数据库：Milvus（Docker）
- 关系数据库：PostgreSQL（Docker）
- 对话模型：DeepSeek（API Key 仅后端环境变量注入；前端不可接触）

## 3. 部署拓扑（本地/测试）

浏览器(Vue) -> Spring Boot -> (PostgreSQL + Milvus + Ollama + DeepSeek)

- Vue：开发端口（例如 8081）
- Spring Boot：8080
- Ollama：11434（本机）
- Milvus：19530（docker）
- PostgreSQL：5432（docker）
- DeepSeek：外部 API（可选；用于兜底/追问话术）

## 4. 核心模块（后端）

- 路由 Router
  - 输入：sessionId + userMessage
  - 输出：answerText + route(intent/override/kb/fallback) + matched(url/title/score) + canFeedbackMismatch
- Intent（可配置）
  - keywords 命中（按 priority）优先处理
  - required_slots 未齐：进入会话状态机追问
  - slots 齐：调用 handler（可模拟）返回业务结果
- Overrides（纠错覆盖层，全局生效）
  - 语义向量检索（Milvus overrides）
  - 命中阈值 override_threshold
  - 命中即返回 chosen_url
- KB（知识库向量检索）
  - 抓取 Zendesk category/section/article，清洗正文
  - chunk 切分 + batch embedding
  - 向量写 Milvus kb_chunks，元数据写 PostgreSQL
  - 查询：chunk topK -> article 聚合 -> top1 返回 URL
- Feedback（纠错闭环）
  - 仅对 route=kb 的回答开放"答案不匹配"
  - 重新检索 KB Top3 候选 -> 用户选择 -> 写 Overrides -> 归档反馈
- Admin（无鉴权，本地/内网使用）
  - KB sources 管理、手动同步/重建
  - Intent 配置编辑（keywords/instructions/slots/priority）
  - Feedback 查看
  - Overrides 查看/启用禁用

## 5. 数据与存储

### 5.1 PostgreSQL（配置/审计/元数据）

建议表（示例）：
- kb_source：知识库入口 URL
- kb_article：文章 title/url/body/hash
- kb_chunk：chunk_text/chunk_no（向量在 Milvus）
- intent_config：keywords、instructions、handler、required_slots、priority、enabled
- session_state：pending_intent + slots（JSON）
- chat_message：对话记录 + route + matched_url + score
- feedback：纠错记录（question/bad_url/bad_score/status）
- feedback_candidate：Top3 候选（rank/url/title/score）
- override_meta：Overrides 审计（question/chosen_url/active）

### 5.2 Milvus（向量存储）

Collections：
1) kb_chunks
- chunk_id(PK), embedding(vector, dim=1024), article_id, source_id

2) overrides
- override_id(PK), embedding(vector, dim=1024), active, chosen_url

向量维度：
- bge-m3 输出 1024 维向量
- 启动时自动探测并初始化 collections

## 6. 关键流程

### 6.1 Chat 路由流程

1. 若 session_state 存在 pending_intent：继续收集 slots，齐全后执行 handler
2. 否则 Intent keywords 命中：进入 handler 或追问 slots
3. 否则 Overrides 向量检索：score >= override_threshold -> 返回 chosen_url
4. 否则 KB 向量检索：score >= kb_threshold -> 返回文章 URL
5. 否则 Fallback：DeepSeek 生成兜底回复（不返回/猜测 URL）

### 6.2 手动同步知识库

Admin 触发同步：
- 抓取 category 页 -> 收集 article URL
- 抓取文章 -> 清洗正文 -> chunk
- batch 调用 Ollama `/api/embed`
- PostgreSQL 写 article/chunk 元数据
- Milvus 写 kb_chunks 向量
- 策略：按 source 全量重建（删除旧数据再写入）

### 6.3 纠错闭环（KB 专用，全局修复）

- 用户对 KB 回答点击"答案不匹配"
- 系统记录 feedback
- 系统对原问题重新检索，返回 Top3 候选 URL（相似度排序）
- 用户选定 1/2/3
- 系统写入 overrides（embedding(question) -> chosen_url），归档 feedback
- 后续相近问题优先命中 overrides（全局生效）

## 7. 配置项（建议可配置）

- kb_threshold（例如 0.75 起）
- override_threshold（例如 0.88 起）
- kb chunk topK（例如 20）
- chunk_size / overlap
- Ollama base_url / model_name
- Milvus host/port/collection 名称

## 8. 安全与合规

- DeepSeek API Key：仅后端环境变量注入，不得提交仓库、不进入前端
- Admin 无鉴权仅限本地/内网；对外发布需加鉴权、审计、限流

## 9. 启动说明

### 9.1 启动基础设施
```bash
cd atome-card-bot/infra
docker-compose up -d
```

### 9.2 确保 Ollama 已安装 bge-m3
```bash
ollama pull bge-m3
ollama list  # 确认 bge-m3 在列表中
```

### 9.3 配置 DeepSeek API Key（可选）
```bash
export DEEPSEEK_API_KEY=sk-YOUR_NEW_KEY_HERE
```

### 9.4 启动后端
```bash
cd atome-card-bot/backend
mvn clean package -DskipTests
java -jar target/atome-card-bot-1.0.0.jar
```

### 9.5 启动前端
```bash
cd atome-card-bot/frontend
npm install
npm run serve
```

### 9.6 首次使用
1. 访问 http://localhost:8081
2. 进入"知识库"页面，点击"同步并重建"抓取 Atome Card 文章
3. 返回聊天页面开始对话

## 10. 已知问题与限制

- DeepSeek 为可选功能，如未配置 API Key，兜底回复将使用本地模拟
- Embedding 模型已确认为 bge-m3（而非之前的 Qwen3-Embedding-8B，后者不支持 embedding API）
- 首次 KB 同步可能需要 1-3 分钟，视网络情况而定
- 当前版本无 Admin 鉴权，仅限内网/本地使用
