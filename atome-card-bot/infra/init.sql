-- Atome Card Bot - 完整数据库初始化脚本
-- 包含表结构、索引、触发器和默认数据

-- ============================================
-- 清理旧表（如果存在）
-- ============================================
DROP TABLE IF EXISTS feedback_candidate CASCADE;
DROP TABLE IF EXISTS feedback CASCADE;
DROP TABLE IF EXISTS session_state CASCADE;
DROP TABLE IF EXISTS chat_message CASCADE;
DROP TABLE IF EXISTS embedding_config CASCADE;
DROP TABLE IF EXISTS override_meta CASCADE;
DROP TABLE IF EXISTS kb_chunk CASCADE;
DROP TABLE IF EXISTS kb_article CASCADE;
DROP TABLE IF EXISTS intent_config CASCADE;
DROP TABLE IF EXISTS kb_source CASCADE;

-- 清理触发器
DROP FUNCTION IF EXISTS update_updated_at_column() CASCADE;

-- ============================================
-- 创建表结构
-- ============================================

-- 知识库源表
CREATE TABLE kb_source (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    url TEXT NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    last_sync_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 知识库文章表
CREATE TABLE kb_article (
    id BIGSERIAL PRIMARY KEY,
    source_id INTEGER REFERENCES kb_source(id) ON DELETE CASCADE,
    title VARCHAR(500),
    url TEXT NOT NULL,
    body_text TEXT,
    hash VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 知识库分块表
CREATE TABLE kb_chunk (
    id BIGSERIAL PRIMARY KEY,
    article_id BIGINT REFERENCES kb_article(id) ON DELETE CASCADE,
    chunk_no INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 意图配置表 (id 使用 SERIAL，与实体类 Integer 匹配)
CREATE TABLE intent_config (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    priority INTEGER DEFAULT 100,
    keywords_json JSONB NOT NULL,
    instructions TEXT,
    handler VARCHAR(100),
    required_slots_json JSONB DEFAULT '[]',
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 聊天消息表
CREATE TABLE chat_message (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('user', 'assistant')),
    text TEXT NOT NULL,
    route VARCHAR(20) CHECK (route IN ('intent', 'override', 'kb', 'fallback')),
    matched_url TEXT,
    matched_id BIGINT,
    score FLOAT,  -- 改为 FLOAT 与实体类匹配
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 会话状态表
CREATE TABLE session_state (
    session_id VARCHAR(64) PRIMARY KEY,
    pending_intent VARCHAR(100),
    slots_json JSONB DEFAULT '{}',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 反馈表
CREATE TABLE feedback (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    question_text TEXT NOT NULL,
    bad_url TEXT,
    bad_score FLOAT,  -- 改为 FLOAT
    status VARCHAR(20) DEFAULT 'open' CHECK (status IN ('open', 'awaiting_choice', 'fixed', 'archived')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP
);

-- 反馈候选表
CREATE TABLE feedback_candidate (
    id BIGSERIAL PRIMARY KEY,
    feedback_id BIGINT REFERENCES feedback(id) ON DELETE CASCADE,
    rank INTEGER NOT NULL CHECK (rank IN (1, 2, 3)),
    url TEXT NOT NULL,
    title VARCHAR(500),
    score FLOAT,  -- 改为 FLOAT
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 覆盖元数据表
CREATE TABLE override_meta (
    id BIGSERIAL PRIMARY KEY,
    feedback_id BIGINT REFERENCES feedback(id),
    question_text TEXT NOT NULL,
    chosen_url TEXT NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 嵌入配置表
CREATE TABLE embedding_config (
    id SERIAL PRIMARY KEY,
    model_name VARCHAR(100) NOT NULL,
    dim INTEGER NOT NULL,
    metric VARCHAR(20) DEFAULT 'COSINE',
    normalize_strategy VARCHAR(20) DEFAULT 'none',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 创建索引
-- ============================================
CREATE INDEX idx_kb_article_source_id ON kb_article(source_id);
CREATE INDEX idx_kb_chunk_article_id ON kb_chunk(article_id);
CREATE INDEX idx_chat_message_session ON chat_message(session_id);
CREATE INDEX idx_chat_message_route ON chat_message(route);
CREATE INDEX idx_feedback_session ON feedback(session_id);
CREATE INDEX idx_feedback_status ON feedback(status);
CREATE UNIQUE INDEX idx_feedback_candidate_feedback_rank ON feedback_candidate(feedback_id, rank);

-- ============================================
-- 创建触发器函数
-- ============================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 创建自动更新触发器
CREATE TRIGGER update_kb_source_updated_at BEFORE UPDATE ON kb_source 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_kb_article_updated_at BEFORE UPDATE ON kb_article 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_intent_config_updated_at BEFORE UPDATE ON intent_config 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- 插入默认数据
-- ============================================

-- 1. 知识库源
INSERT INTO kb_source (id, name, url, enabled, created_at, updated_at) 
VALUES (1, 'Atome Card', 'https://help.atome.ph/hc/en-gb/categories/4439682039065-Atome-Card', TRUE, NOW(), NOW());

-- 2. 知识库文章
INSERT INTO kb_article (id, source_id, title, url, body_text, hash, created_at, updated_at) VALUES
(1, 1, 'What is Atome card?', 'https://help.atome.ph/hc/en-gb/articles/8712898781593', 
 'Atome Card is a credit card that allows you to pay in 3 or 6 monthly installments. It offers both physical and virtual card options for your convenience.', 
 'hash1', NOW(), NOW()),
(2, 1, 'How can I check the status of my application?', 'https://help.atome.ph/hc/en-gb/articles/8712978836377', 
 'You can check your application status by logging into the Atome app and navigating to the Card section. The status will show as Pending, Approved, or Rejected.', 
 'hash2', NOW(), NOW()),
(3, 1, 'Why did my Card application fail?', 'https://help.atome.ph/hc/en-gb/articles/43466854486681', 
 'Your application may fail due to incomplete documentation, insufficient credit history, or not meeting eligibility criteria. Please ensure all required documents are submitted.', 
 'hash3', NOW(), NOW()),
(4, 1, 'Where can I use my Atome card?', 'https://help.atome.ph/hc/en-gb/articles/44697622305049', 
 'You can use your Atome card at any merchant that accepts Visa. This includes online stores, physical retail locations, and ATMs worldwide.', 
 'hash4', NOW(), NOW()),
(5, 1, 'Why did my card transaction fail?', 'https://help.atome.ph/hc/en-gb/articles/43466116960153', 
 'Transaction failures can occur due to insufficient balance, incorrect card details, merchant restrictions, or security blocks. Please check your available spending limit and try again.', 
 'hash5', NOW(), NOW());

-- 3. 知识库分块
INSERT INTO kb_chunk (id, article_id, chunk_no, chunk_text, created_at) VALUES
(1, 1, 0, 'Atome Card is a credit card that allows you to pay in 3 or 6 monthly installments. It offers both physical and virtual card options for your convenience.', NOW()),
(2, 2, 0, 'You can check your application status by logging into the Atome app and navigating to the Card section. The status will show as Pending, Approved, or Rejected.', NOW()),
(3, 3, 0, 'Your application may fail due to incomplete documentation, insufficient credit history, or not meeting eligibility criteria. Please ensure all required documents are submitted.', NOW()),
(4, 4, 0, 'You can use your Atome card at any merchant that accepts Visa. This includes online stores, physical retail locations, and ATMs worldwide.', NOW()),
(5, 5, 0, 'Transaction failures can occur due to insufficient balance, incorrect card details, merchant restrictions, or security blocks. Please check your available spending limit and try again.', NOW());

-- 4. 意图配置
INSERT INTO intent_config (id, name, priority, keywords_json, instructions, handler, required_slots_json, enabled, created_at, updated_at) VALUES
(1, 'CARD_APPLICATION_STATUS', 10, 
 '["申请进度", "申请状态", "办卡进度", "application status"]', 
 '询问卡片申请进度时，需要用户提供申请标识（手机号或申请编号）', 
 'cardApplicationHandler', 
 '["application_id"]', 
 TRUE, NOW(), NOW()),
(2, 'FAILED_TRANSACTION', 20, 
 '["交易失败", "失败交易", "transaction failed", "declined"]', 
 '询问失败交易时，要求用户提供交易ID', 
 'failedTransactionHandler', 
 '["transaction_id"]', 
 TRUE, NOW(), NOW());

-- 5. 嵌入配置
INSERT INTO embedding_config (id, model_name, dim, metric, normalize_strategy, created_at) 
VALUES (1, 'bge-m3', 1024, 'COSINE', 'none', NOW());

-- ============================================
-- 重置序列
-- ============================================
SELECT setval('kb_source_id_seq', 1);
SELECT setval('kb_article_id_seq', 5);
SELECT setval('kb_chunk_id_seq', 5);
SELECT setval('intent_config_id_seq', 2);
SELECT setval('embedding_config_id_seq', 1);

-- ============================================
-- 打印初始化完成信息
-- ============================================
DO $$
BEGIN
    RAISE NOTICE '========================================';
    RAISE NOTICE '数据库初始化完成！';
    RAISE NOTICE '- 知识库源: 1 个';
    RAISE NOTICE '- 知识库文章: 5 篇';
    RAISE NOTICE '- 知识库分块: 5 个';
    RAISE NOTICE '- 意图配置: 2 个';
    RAISE NOTICE '- 嵌入配置: 1 个';
    RAISE NOTICE '========================================';
    RAISE NOTICE '注意：向量数据需要在应用启动后由';
    RAISE NOTICE 'DataInitializer 自动生成到 Milvus';
    RAISE NOTICE '========================================';
END $$;
