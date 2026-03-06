# 功能文档（CN）

本文件描述"需求功能点、用户交互、业务规则、验收用例"，用于后续生成代码与验证核对。

## 1. 功能范围

### 1.1 对话问答（Chat）

- 输入：用户问题
- 输出：
  - 命中意图：按意图说明追问/调用函数返回状态
  - 命中知识库：返回帮助中心文章 URL
  - 未命中：兜底回复（建议改写/提供入口/转人工）

### 1.2 知识库问答（向量检索返回 URL）

- 知识库来源：Zendesk Atome Card 分类页及其文章
- 构建方式：抓取文章 -> 清洗文本 -> chunk -> embedding（bge-m3）-> 写入 Milvus
- 查询方式：对用户问题 embedding -> topK 检索 -> article 聚合 -> 命中阈值后返回 URL

### 1.3 意图处理（优先于知识库，可配置）

意图配置包含：

- 关键字 keywords（包含匹配）
- 优先级 priority（越小越优先）
- 附加说明 instructions（追问策略/响应规则）
- required_slots（需要用户提供的字段）
- handler（处理函数名，可模拟实现）
- enabled（启用/禁用）

内置意图（示例）：

1) 卡片申请进度
- 命中后：若缺少 application_id（或你定义的标识）则追问；齐全后调用函数返回进度

2) 失败交易查询
- 命中后：要求 transaction_id；齐全后调用函数返回交易状态

### 1.4 可视化管理后台（Vue）

- 知识库 URL 管理
  - 新增/编辑/启用禁用 KB Source URL
  - 手动触发"同步并重建"
- 意图配置管理
  - 编辑 keywords、instructions、priority、required_slots、enabled
  - 保存后立即生效（无需重启）
- 反馈中心
  - 查看反馈列表与状态（open/awaiting_choice/fixed/archived）
  - 查看问题描述与错误链接等信息
- Overrides 管理
  - 查看全局覆盖规则列表
  - 启用/禁用（禁用后应立即不再优先命中）

### 1.5 用户纠错与自动修复（KB 专用）

- 触发：仅对 route=kb 的回答提供"答案不匹配"
- 流程：
  1) 用户点击"答案不匹配"
  2) 系统记录 feedback（包含用户问题、错误 URL、相似度等）
  3) 系统对原问题重新向量检索，返回按相似度排序的 Top3 候选 URL
  4) 用户选择 1/2/3 作为正确答案 URL
  5) 系统生成全局 Overrides（问题向量 -> chosen_url），并归档该反馈
- 效果：后续用户提出相似问题，优先返回该 chosen_url

## 2. 核心业务规则（必须满足）

- 路由优先级：Intent > Overrides > KB > Fallback
- 意图命中后不再走 KB/Overrides
- 纠错功能只作用于 KB 返回（route=kb）
- 用户选定候选 URL 后，Overrides 全局生效且优先于 KB
- 禁用 Overrides 后应立即失效（后续同义问题回到 KB 检索）

## 3. API 行为约束（验收用）

- Chat 返回需包含：
  - route（intent/override/kb/fallback）
  - matched url/title/score（若适用）
  - canFeedbackMismatch（仅 route=kb 为 true）
- Feedback start：
  - 返回 feedbackId + Top3 candidates（rank/title/url/score）
- Feedback choose：
  - 写入 override 并返回 chosenUrl
  - 反馈状态进入 archived（或 fixed+archived）

## 4. 验收用例（建议）

### 4.1 手动同步知识库
- Admin 点击同步并重建
- 期望：文章数/分块数 > 0；后续 KB 查询可命中

### 4.2 知识库命中返回 URL
- 输入：典型 Atome Card 问题
- 期望：route=kb；返回 Zendesk URL；显示"答案不匹配"按钮

### 4.3 意图优先
- 输入："申请进度/申请状态"
- 期望：route=intent；追问 application_id；输入后返回模拟进度

### 4.4 纠错闭环生效（全局）
- 对某条 route=kb 的回答点击"答案不匹配"
- 期望：返回 Top3 候选；选 2 后显示"已修复"
- 再提同义问题
- 期望：route=override；返回 chosen_url

### 4.5 禁用 Overrides
- 在 Admin 禁用该 override
- 再提同义问题
- 期望：不命中 override，回到 KB 或 fallback

## 5. 启动与测试指南

### 5.1 启动服务
```bash
# 1. 启动基础设施
cd atome-card-bot/infra
docker-compose up -d

# 2. 确保 Ollama 已安装 bge-m3
ollama pull bge-m3

# 3. 启动后端（可选配置 DeepSeek API Key）
cd atome-card-bot/backend
mvn clean package -DskipTests
export DEEPSEEK_API_KEY=your_key_here  # 可选
java -jar target/atome-card-bot-1.0.0.jar

# 4. 启动前端
cd atome-card-bot/frontend
npm install
npm run serve
```

### 5.2 测试步骤
1. 访问 http://localhost:8081
2. 进入"知识库"页面，点击"同步并重建"
3. 等待同步完成（显示文章数/分块数）
4. 返回聊天页面，测试常见问题
5. 对 KB 回答点击"答案不匹配"，测试纠错闭环

## 6. 当前版本不做（明确边界）

- 国际化意图匹配（中英自动映射/翻译/语义 intent）
- 自动抽取 slot（例如一句话自动解析 transaction_id）
- 增量同步（当前按 source 全量重建）
- Admin 登录鉴权（当前不需要）

## 7. 已知问题

- DeepSeek API Key 为可选配置，如未配置，兜底回复功能受限
- 首次 KB 同步可能需要 1-3 分钟，视网络情况而定
- Admin 无鉴权，仅限内网/本地使用
- Embedding 模型使用 bge-m3（1024 维），启动时自动探测维度并初始化 Milvus collections
