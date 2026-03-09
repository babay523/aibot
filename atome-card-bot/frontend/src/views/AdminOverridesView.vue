<template>
  <div class="admin-container">
    <h1>全局纠错规则</h1>
    
    <div class="override-list">
      <div v-for="item in overrides" :key="item.id" class="override-card">
        <div class="override-header">
          <span class="status" :class="item.active ? 'active' : 'inactive'">
            {{ item.active ? '生效中' : '已禁用' }}
          </span>
          <span class="time">{{ formatTime(item.createdAt) }}</span>
        </div>
        
        <div class="question">
          <label>触发问题</label>
          <p>{{ item.questionText }}</p>
        </div>
        
        <div class="chosen">
          <label>优先返回</label>
          <a :href="item.chosenUrl" target="_blank">{{ item.chosenUrl }}</a>
        </div>
        
        <div class="actions">
          <button 
            @click="toggleActive(item)" 
            class="btn-toggle"
            :class="item.active ? 'disable' : 'enable'"
          >
            {{ item.active ? '禁用' : '启用' }}
          </button>
          <button 
            @click="deleteItem(item)" 
            class="btn-delete"
          >
            删除
          </button>
        </div>
      </div>
    </div>
    
    <div v-if="overrides.length === 0" class="empty">
      暂无纠错规则
    </div>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { adminAPI } from '../api'

export default {
  name: 'AdminOverridesView',
  setup() {
    const overrides = ref([])

    const formatTime = (time) => {
      if (!time) return '-'
      return new Date(time).toLocaleString('zh-CN')
    }

    const loadOverrides = async () => {
      try {
        const response = await adminAPI.listOverrides()
        overrides.value = response.data
      } catch (error) {
        alert('加载失败: ' + error.message)
      }
    }

    const toggleActive = async (item) => {
      try {
        await adminAPI.updateOverride(item.id, { active: !item.active })
        item.active = !item.active
      } catch (error) {
        alert('更新失败: ' + error.message)
      }
    }

    const deleteItem = async (item) => {
      if (!confirm(`确定要删除这条纠错规则吗？\n\n问题：${item.questionText.substring(0, 50)}...`)) {
        return
      }
      
      try {
        await adminAPI.deleteOverride(item.id)
        // 从列表中移除
        overrides.value = overrides.value.filter(o => o.id !== item.id)
        alert('删除成功')
      } catch (error) {
        alert('删除失败: ' + error.message)
      }
    }

    onMounted(loadOverrides)

    return { overrides, formatTime, toggleActive, deleteItem }
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

.override-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.override-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.override-header {
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

.status.active {
  background: #e8f5e9;
  color: #2e7d32;
}

.status.inactive {
  background: #ffebee;
  color: #c62828;
}

.time {
  font-size: 13px;
  color: #999;
}

.question, .chosen {
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

.chosen a {
  color: #1976d2;
  word-break: break-all;
}

.actions {
  display: flex;
  justify-content: flex-end;
}

.btn-toggle {
  padding: 6px 14px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}

.btn-toggle.enable {
  background: #4caf50;
  color: #fff;
}

.btn-toggle.disable {
  background: #f44336;
  color: #fff;
}

.empty {
  text-align: center;
  padding: 40px;
  color: #999;
  background: #f5f5f5;
  border-radius: 8px;
}
</style>