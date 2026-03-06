<template>
  <div class="admin-container">
    <h1>反馈记录</h1>
    
    <div class="filter">
      <select v-model="statusFilter" @change="loadFeedback">
        <option value="">全部</option>
        <option value="open">待处理</option>
        <option value="awaiting_choice">等待选择</option>
        <option value="fixed">已修复</option>
        <option value="archived">已归档</option>
      </select>
    </div>
    
    <div class="feedback-list">
      <div v-for="item in feedback" :key="item.id" class="feedback-card">
        <div class="feedback-header">
          <span class="status" :class="item.status">{{ getStatusLabel(item.status) }}</span>
          <span class="time">{{ formatTime(item.createdAt) }}</span>
        </div>
        
        <div class="question">
          <label>用户问题</label>
          <p>{{ item.questionText }}</p>
        </div>
        
        <div class="bad-answer" v-if="item.badUrl">
          <label>错误回答</label>
          <a :href="item.badUrl" target="_blank">{{ item.badUrl }}</a>
          <span class="score">相似度: {{ (item.badScore * 100).toFixed(1) }}%</span>
        </div>
        
        <div class="resolved" v-if="item.resolvedAt">
          <label>解决时间</label>
          <span>{{ formatTime(item.resolvedAt) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { adminAPI } from '../api'

export default {
  name: 'AdminFeedbackView',
  setup() {
    const feedback = ref([])
    const statusFilter = ref('')

    const getStatusLabel = (status) => {
      const labels = {
        'open': '待处理',
        'awaiting_choice': '等待选择',
        'fixed': '已修复',
        'archived': '已归档'
      }
      return labels[status] || status
    }

    const formatTime = (time) => {
      if (!time) return '-'
      return new Date(time).toLocaleString('zh-CN')
    }

    const loadFeedback = async () => {
      try {
        const response = await adminAPI.listFeedback(statusFilter.value || null)
        feedback.value = response.data
      } catch (error) {
        alert('加载失败: ' + error.message)
      }
    }

    onMounted(loadFeedback)

    return { feedback, statusFilter, getStatusLabel, formatTime, loadFeedback }
  }
}
</script>

<style scoped>
.admin-container {
  max-width: 1000px;
  margin: 20px auto;
  padding: 0 20px;
}

h1 {
  font-size: 24px;
  margin-bottom: 24px;
}

.filter {
  margin-bottom: 20px;
}

.filter select {
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.feedback-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feedback-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.feedback-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.status {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 12px;
}

.status.open { background: #fff3e0; color: #e65100; }
.status.awaiting_choice { background: #e3f2fd; color: #1976d2; }
.status.fixed { background: #e8f5e9; color: #2e7d32; }
.status.archived { background: #f5f5f5; color: #666; }

.time {
  font-size: 13px;
  color: #999;
}

.question, .bad-answer, .resolved {
  margin-bottom: 12px;
}

label {
  display: block;
  font-size: 12px;
  color: #666;
  margin-bottom: 4px;
}

.question p {
  background: #f5f5f5;
  padding: 10px;
  border-radius: 4px;
  line-height: 1.5;
}

.bad-answer a {
  color: #1976d2;
  word-break: break-all;
}

.score {
  font-size: 12px;
  color: #999;
  margin-left: 8px;
}
</style>