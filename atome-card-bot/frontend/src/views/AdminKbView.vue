<template>
  <div class="admin-container">
    <h1>知识库管理</h1>
    
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <span class="total">共 {{ articles.length }} 篇文章</span>
      </div>
      <div class="toolbar-right">
        <button 
          @click="showAddModal = true" 
          class="btn-add"
        >
          ➕ 新增文章
        </button>
        <button 
          @click="syncArticles" 
          class="btn-sync"
          :disabled="syncing"
        >
          {{ syncing ? '同步中...' : '🔄 同步并重建' }}
        </button>
      </div>
    </div>

    <!-- 文章列表 -->
    <div class="articles-list">
      <div v-for="article in articles" :key="article.id" class="article-card">
        <div class="article-header">
          <h3 class="article-title">{{ article.title }}</h3>
          <a :href="article.url" target="_blank" class="article-link">查看原文 →</a>
        </div>
        <div class="article-body">
          <p>{{ truncateText(article.bodyText, 300) }}</p>
        </div>
        <div class="article-meta">
          <span class="meta-item">ID: {{ article.id }}</span>
          <span class="meta-item" v-if="article.sourceId">来源ID: {{ article.sourceId }}</span>
          <button @click="deleteArticle(article)" class="btn-delete">删除</button>
        </div>
      </div>
      
      <div v-if="articles.length === 0" class="empty-state">
        <p>暂无文章数据</p>
        <p class="hint">点击上方"同步并重建"按钮抓取文章</p>
      </div>
    </div>
    
    <div v-if="syncResult" class="sync-result">
      <h3>同步结果</h3>
      <p>文章数: {{ syncResult.articleCount }}</p>
      <p>分块数: {{ syncResult.chunkCount }}</p>
      <p>耗时: {{ syncResult.durationMs }}ms</p>
    </div>

    <!-- 新增文章模态框 -->
    <div v-if="showAddModal" class="modal-overlay" @click.self="showAddModal = false">
      <div class="modal-content">
        <h2>新增知识库文章</h2>
        <form @submit.prevent="submitAdd">
          <div class="form-group">
            <label>来源ID</label>
            <input v-model.number="newArticle.sourceId" type="number" required placeholder="输入来源ID">
          </div>
          <div class="form-group">
            <label>标题</label>
            <input v-model="newArticle.title" type="text" required placeholder="输入文章标题">
          </div>
          <div class="form-group">
            <label>URL</label>
            <input v-model="newArticle.url" type="url" required placeholder="https://...">
          </div>
          <div class="form-group">
            <label>正文内容</label>
            <textarea v-model="newArticle.bodyText" rows="6" required placeholder="输入文章正文..."></textarea>
          </div>
          <div class="form-actions">
            <button type="button" @click="showAddModal = false" class="btn-cancel">取消</button>
            <button type="submit" class="btn-submit" :disabled="adding">
              {{ adding ? '提交中...' : '提交' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { adminAPI } from '../api'

export default {
  name: 'AdminKbView',
  setup() {
    const articles = ref([])
    const syncing = ref(false)
    const syncResult = ref(null)
    const showAddModal = ref(false)
    const adding = ref(false)
    const newArticle = ref({
      sourceId: 6,
      title: '',
      url: '',
      bodyText: ''
    })

    const loadArticles = async () => {
      try {
        // 获取所有文章（不传 sourceId 表示获取全部）
        const response = await adminAPI.listKbArticles()
        articles.value = response.data
      } catch (error) {
        console.error('加载文章失败:', error)
        alert('加载文章失败: ' + error.message)
      }
    }

    const truncateText = (text, maxLength) => {
      if (!text) return ''
      if (text.length <= maxLength) return text
      return text.substring(0, maxLength) + '...'
    }

    const syncArticles = async () => {
      syncing.value = true
      try {
        // 使用 sourceId=1 进行同步（默认源）
        const response = await adminAPI.syncKb(1, true)
        syncResult.value = response.data
        alert('同步成功！')
        // 刷新文章列表
        loadArticles()
      } catch (error) {
        alert('同步失败: ' + error.message)
      } finally {
        syncing.value = false
      }
    }

    const submitAdd = async () => {
      adding.value = true
      try {
        const response = await adminAPI.createKbArticle(newArticle.value)
        if (response.data.success) {
          alert('文章新增成功！')
          showAddModal.value = false
          // 清空表单
          newArticle.value = {
            sourceId: 6,
            title: '',
            url: '',
            bodyText: ''
          }
          // 刷新列表
          loadArticles()
        } else {
          alert('新增失败: ' + (response.data.error || '未知错误'))
        }
      } catch (error) {
        alert('新增失败: ' + error.message)
      } finally {
        adding.value = false
      }
    }

    const deleteArticle = async (article) => {
      if (!confirm(`确定要删除这篇文章吗？\n\n标题：${article.title}\nID：${article.id}`)) {
        return
      }

      try {
        const response = await adminAPI.deleteKbArticle(article.id)
        if (response.data.success) {
          alert('删除成功')
          // 从列表中移除
          articles.value = articles.value.filter(a => a.id !== article.id)
        } else {
          alert('删除失败: ' + (response.data.error || '未知错误'))
        }
      } catch (error) {
        alert('删除失败: ' + error.message)
      }
    }

    onMounted(() => {
      loadArticles()
    })

    return {
      articles,
      syncing,
      syncResult,
      showAddModal,
      adding,
      newArticle,
      loadArticles,
      truncateText,
      syncArticles,
      submitAdd,
      deleteArticle
    }
  }
}
</script>

<style scoped>
.admin-container {
  max-width: 1200px;
  margin: 20px auto;
  padding: 0 20px;
}

h1 {
  font-size: 24px;
  margin-bottom: 24px;
  color: #333;
}

/* 工具栏 */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.toolbar-left .total {
  font-size: 14px;
  color: #666;
}

.toolbar-right {
  display: flex;
  gap: 12px;
}

.btn-sync {
  padding: 8px 16px;
  background: #ff9800;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.2s;
}

.btn-sync:hover:not(:disabled) {
  background: #f57c00;
}

.btn-sync:disabled {
  background: #ccc;
  cursor: not-allowed;
}

/* 文章列表 */
.articles-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.article-card {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 20px;
  transition: box-shadow 0.2s;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.article-card:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.article-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.article-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0;
  flex: 1;
  line-height: 1.4;
}

.article-link {
  font-size: 13px;
  color: #1976d2;
  text-decoration: none;
  margin-left: 12px;
  white-space: nowrap;
  padding: 4px 8px;
  border-radius: 4px;
  background: #e3f2fd;
}

.article-link:hover {
  background: #1976d2;
  color: #fff;
}

.article-body {
  color: #555;
  font-size: 14px;
  line-height: 1.7;
  margin-bottom: 12px;
}

.article-body p {
  margin: 0;
}

.article-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #999;
}

.meta-item {
  background: #f5f5f5;
  padding: 3px 10px;
  border-radius: 4px;
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 60px 20px;
  background: #fafafa;
  border-radius: 8px;
  border: 2px dashed #e0e0e0;
}

.empty-state p {
  margin: 8px 0;
  color: #999;
}

.empty-state .hint {
  font-size: 13px;
  color: #1976d2;
}

/* 同步结果 */
.sync-result {
  background: #e8f5e9;
  border: 1px solid #a5d6a7;
  border-radius: 8px;
  padding: 16px;
  margin-top: 20px;
}

.sync-result h3 {
  margin-bottom: 8px;
  color: #2e7d32;
  font-size: 16px;
}

.sync-result p {
  margin: 4px 0;
  font-size: 14px;
  color: #555;
}

/* 新增按钮 */
.btn-add {
  padding: 8px 16px;
  background: #4caf50;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.2s;
}

.btn-add:hover {
  background: #45a049;
}

/* 删除按钮 */
.btn-delete {
  margin-left: auto;
  padding: 4px 12px;
  background: #f44336;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  transition: background 0.2s;
}

.btn-delete:hover {
  background: #d32f2f;
}

/* 模态框 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal-content {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  width: 90%;
  max-width: 600px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
}

.modal-content h2 {
  margin-bottom: 20px;
  color: #333;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  font-size: 14px;
  color: #555;
  margin-bottom: 6px;
  font-weight: 500;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  box-sizing: border-box;
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: #1976d2;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 20px;
}

.btn-cancel {
  padding: 10px 20px;
  background: #f5f5f5;
  color: #333;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

.btn-cancel:hover {
  background: #e0e0e0;
}

.btn-submit {
  padding: 10px 20px;
  background: #1976d2;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

.btn-submit:hover:not(:disabled) {
  background: #1565c0;
}

.btn-submit:disabled {
  background: #ccc;
  cursor: not-allowed;
}
</style>