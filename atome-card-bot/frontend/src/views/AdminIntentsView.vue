<template>
  <div class="admin-container">
    <h1>意图配置</h1>
    
    <div class="section">
      <div class="section-header">
        <h2>意图列表</h2>
        <button @click="showCreateModal = true" class="btn-primary">
          + 新增意图
        </button>
      </div>
      
      <div class="intent-list">
        <div v-for="intent in intents" :key="intent.id" class="intent-card">
          <div class="intent-header">
            <h3>{{ intent.name }}</h3>
            <span class="priority">优先级: {{ intent.priority }}</span>
          </div>
          
          <div class="form-group">
            <label>关键词 (JSON 数组)</label>
            <textarea 
              v-model="intent.keywordsJson" 
              @blur="updateIntent(intent)"
              rows="2"
              class="input-textarea"
            ></textarea>
          </div>
          
          <div class="form-group">
            <label>附加说明</label>
            <textarea 
              v-model="intent.instructions" 
              @blur="updateIntent(intent)"
              rows="2"
              class="input-textarea"
            ></textarea>
          </div>
          
          <div class="form-row">
            <div class="form-group">
              <label>Handler</label>
              <input v-model="intent.handler" @blur="updateIntent(intent)" class="input" />
            </div>
            <div class="form-group">
              <label>Required Slots (JSON)</label>
              <input v-model="intent.requiredSlotsJson" @blur="updateIntent(intent)" class="input" />
            </div>
          </div>
          
          <div class="form-row">
            <div class="form-group">
              <label>优先级</label>
              <input 
                type="number" 
                v-model.number="intent.priority" 
                @blur="updateIntent(intent)" 
                class="input small"
              />
            </div>
            <label class="checkbox">
              <input 
                type="checkbox" 
                v-model="intent.enabled"
                @change="updateIntent(intent)"
              />
              启用
            </label>
          </div>
        </div>
      </div>
    </div>

    <!-- 新增意图弹窗 -->
    <div v-if="showCreateModal" class="modal-overlay" @click="showCreateModal = false">
      <div class="modal-content" @click.stop>
        <h2>新增意图</h2>
        
        <div class="form-group">
          <label>意图名称</label>
          <input v-model="newIntent.name" class="input" placeholder="如: CARD_APPLICATION_STATUS" />
        </div>
        
        <div class="form-group">
          <label>关键词 (JSON 数组)</label>
          <textarea v-model="newIntent.keywordsJson" rows="2" class="input-textarea" placeholder='["关键词1", "关键词2"]'></textarea>
        </div>
        
        <div class="form-group">
          <label>附加说明</label>
          <textarea v-model="newIntent.instructions" rows="2" class="input-textarea" placeholder="描述该意图的处理逻辑"></textarea>
        </div>
        
        <div class="form-row">
          <div class="form-group">
            <label>Handler</label>
            <input v-model="newIntent.handler" class="input" placeholder="如: cardApplicationHandler" />
          </div>
          <div class="form-group">
            <label>Required Slots (JSON)</label>
            <input v-model="newIntent.requiredSlotsJson" class="input" placeholder='["slotName"]' />
          </div>
        </div>
        
        <div class="form-row">
          <div class="form-group">
            <label>优先级</label>
            <input type="number" v-model.number="newIntent.priority" class="input small" placeholder="数字越小越优先" />
          </div>
          <label class="checkbox">
            <input type="checkbox" v-model="newIntent.enabled" />
            启用
          </label>
        </div>
        
        <div class="modal-actions">
          <button @click="showCreateModal = false" class="btn-secondary">取消</button>
          <button @click="createIntent" class="btn-primary" :disabled="!isValidNewIntent">
            创建
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted, computed } from 'vue'
import { adminAPI } from '../api'

export default {
  name: 'AdminIntentsView',
  setup() {
    const intents = ref([])
    const showCreateModal = ref(false)
    const newIntent = ref({
      name: '',
      keywordsJson: '',
      instructions: '',
      handler: '',
      requiredSlotsJson: '',
      priority: 100,
      enabled: true
    })

    const isValidNewIntent = computed(() => {
      return newIntent.value.name && 
             newIntent.value.keywordsJson && 
             newIntent.value.handler
    })

    const loadIntents = async () => {
      try {
        const response = await adminAPI.listIntents()
        intents.value = response.data
      } catch (error) {
        alert('加载失败: ' + error.message)
      }
    }

    const updateIntent = async (intent) => {
      try {
        await adminAPI.updateIntent(intent.id, {
          keywordsJson: intent.keywordsJson,
          instructions: intent.instructions,
          handler: intent.handler,
          requiredSlotsJson: intent.requiredSlotsJson,
          priority: intent.priority,
          enabled: intent.enabled
        })
      } catch (error) {
        alert('更新失败: ' + error.message)
        loadIntents()
      }
    }

    const createIntent = async () => {
      try {
        await adminAPI.createIntent(newIntent.value)
        showCreateModal.value = false
        // 重置表单
        newIntent.value = {
          name: '',
          keywordsJson: '',
          instructions: '',
          handler: '',
          requiredSlotsJson: '',
          priority: 100,
          enabled: true
        }
        loadIntents()
        alert('创建成功！')
      } catch (error) {
        alert('创建失败: ' + error.message)
      }
    }

    onMounted(loadIntents)

    return { 
      intents, 
      showCreateModal,
      newIntent,
      isValidNewIntent,
      loadIntents, 
      updateIntent,
      createIntent
    }
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

.section {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

h2 {
  font-size: 18px;
  color: #555;
  margin: 0;
}

.intent-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.intent-card {
  background: #f9f9f9;
  border-radius: 8px;
  padding: 20px;
  border: 1px solid #e0e0e0;
}

.intent-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.intent-header h3 {
  font-size: 18px;
  color: #333;
  margin: 0;
}

.priority {
  font-size: 13px;
  color: #666;
  background: #f0f0f0;
  padding: 4px 10px;
  border-radius: 12px;
}

.form-group {
  margin-bottom: 12px;
}

.form-group label {
  display: block;
  font-size: 13px;
  color: #666;
  margin-bottom: 4px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}

.input, .input-textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}

.input-textarea {
  resize: vertical;
  font-family: monospace;
  font-size: 13px;
}

.input.small {
  width: 100px;
}

.checkbox {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  cursor: pointer;
}

.btn-primary {
  padding: 8px 16px;
  background: #1976d2;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.btn-primary:hover:not(:disabled) {
  background: #1565c0;
}

.btn-primary:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.btn-secondary {
  padding: 8px 16px;
  background: #f5f5f5;
  color: #333;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
}

.btn-secondary:hover {
  background: #e0e0e0;
}

/* 弹窗样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
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
  box-shadow: 0 4px 12px rgba(0,0,0,0.3);
}

.modal-content h2 {
  margin-bottom: 20px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #e0e0e0;
}
</style>