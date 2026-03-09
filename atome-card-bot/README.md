# Atome Card Bot - 智能客服机器人

## 项目结构

```
atome-card-bot/
├── infra/                  # 基础设施（Docker Compose）
│   ├── docker-compose.yml  # Milvus + PostgreSQL
│   └── init.sql            # 数据库初始化脚本
├── backend/                # Spring Boot 后端
│   ├── src/
│   │   └── main/java/com/atome/bot/
│   │       ├── client/     # OllamaClient, DeepSeekClient
│   │       ├── config/     # MilvusConfig, InitializationConfig
│   │       ├── controller/ # ChatController, FeedbackController, AdminController
│   │       ├── entity/     # 数据库实体
│   │       ├── model/      # MilvusVectorRow, SearchHit, OverrideVectorRow
│   │       ├── repository/ # JPA Repositories
│   │       └── service/    # 核心业务服务
│   ├── pom.xml
│   └── src/main/resources/
│       └── application.yml
├── frontend/               # Vue 3 前端
│   ├── src/
│   │   ├── api/            # API 客户端
│   │   ├── components/     # 可复用组件
│   │   ├── views/          # 页面视图
│   │   │   ├── ChatView.vue
│   │   │   ├── AdminKbView.vue
│   │   │   ├── AdminIntentsView.vue
│   │   │   ├── AdminFeedbackView.vue
│   │   │   └── AdminOverridesView.vue
│   │   ├── router/         # Vue Router
│   │   ├── App.vue
│   │   └── main.js
│   ├── package.json
│   └── public/index.html
└── docs/                   # 技术文档
    ├── TECH_ARCHITECTURE_CN.md
    └── FUNCTIONAL_SPEC_CN.md
```

## 快速开始

### 1. 启动基础设施

```bash
cd atome-card-bot/infra
docker-compose up -d
```

等待服务启动（首次启动可能需要 1-2 分钟）：
- Milvus: http://localhost:19530
- PostgreSQL: localhost:5432

### 2. 确保 Ollama 已安装 bge-m3

本项目使用本地 Ollama 的 embedding 模型生成向量，默认模型：

- `bge-m3`（BAAI/bge-m3，跨语言检索，1024 维）

确认模型已安装：

```bash
ollama list
```

如果未安装：

```bash
ollama pull bge-m3
```

确保 Ollama 服务在运行（默认 `http://localhost:11434`）。

### 3. 配置 DeepSeek API Key（可选）

编辑 `backend/src/main/resources/application.yml`：

```yaml
deepseek:
  api-key: sk-YOUR_NEW_KEY_HERE  # 替换为你的新 key
```

或者在启动时设置环境变量：
```bash
export DEEPSEEK_API_KEY=sk-YOUR_NEW_KEY_HERE
```

### 4. 启动后端

```bash
cd atome-card-bot/backend
mvn clean install -DskipTests
mvn spring-boot:run
```

后端启动时会：
1. 连接 Ollama 探测 embedding 维度（bge-m3 为 1024 维）
2. 检查/创建 Milvus collections
3. 初始化数据库表

服务地址：http://localhost:8080

### 5. 启动前端

```bash
cd atome-card-bot/frontend
npm install
npm run serve
```

前端地址：http://localhost:8081

### 6. 首次使用

1. 访问前端 http://localhost:8081
2. 进入 "知识库" 页面
3. 点击 "同步并重建" 按钮抓取 Atome Card 帮助中心文章(暂不实现)
4. 等待同步完成（文章数约 100+，耗时 1-3 分钟）
5. 返回聊天页面开始对话

## 核心功能

### 对话路由
- **意图**：关键词匹配优先（申请进度、失败交易等）
- **Overrides**：用户纠错后全局生效
- **KB**：向量检索匹配知识库文章
- **Fallback**：DeepSeek 兜底回复

### 纠错闭环
- 对 KB 回答显示 "答案不匹配" 按钮
- 重新检索 Top3 候选供用户选择
- 用户选择后创建全局 Override
- 后续同义问题优先返回指定 URL

### 管理功能
- 编辑 KB Source URL
- 配置意图关键词和说明
- 查看反馈记录和修复状态
- 启用/禁用全局 Override 规则

## 配置文件

### application.yml 关键配置

```yaml
# Ollama Embedding 模型
ollama:
  base-url: http://localhost:11434
  embed-model: bge-m3  # BAAI/bge-m3，1024 维

# 相似度阈值（按数据校准）
bot:
  thresholds:
    override: 0.88  # 纠错优先阈值（更保守）
    kb: 0.75        # 知识库阈值
```

## 开发里程碑

- [x] 里程碑 1: Ollama probe + Milvus 初始化 + 数据库
- [x] 里程碑 2: KB 同步（抓取 + chunk + embed）
- [x] 里程碑 3: 对话路由（Intent/Override/KB/Fallback）
- [x] 里程碑 4: 纠错闭环（Top3 候选 → Override）
- [x] 里程碑 5: Vue 前端（Chat + Admin）

## 注意事项

1. **DeepSeek API Key**: 切勿提交到代码仓库，仅通过环境变量或本地配置文件注入
2. **Milvus 维度**: 使用 bge-m3（1024 维），启动时自动探测
3. **Ollama 内存**: bge-m3 约占用 1-2GB 内存，确保本地有足够资源
4. **首次同步**: 可能需要 1-3 分钟，视网络情况而定
5. **Admin 鉴权**: 当前版本无鉴权，仅限内网/本地使用

## 技术文档

- [技术架构文档](./docs/TECH_ARCHITECTURE_CN.md)
- [功能文档](./docs/FUNCTIONAL_SPEC_CN.md)

## 演示视频

- [demo](./docs/demo.gif)
