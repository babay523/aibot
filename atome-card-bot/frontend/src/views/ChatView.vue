<template>
  <div class="chat-container">
    <div class="chat-header">
      <h2>Atome Card 智能客服</h2>
      <div class="header-actions">
        <span class="session-id">会话: {{ sessionId }}</span>
        <button @click="clearHistory" class="btn-clear" title="清空聊天记录">
          🗑️ 清空
        </button>
      </div>
    </div>
    
    <div class="messages" ref="messagesContainer">
      <div 
        v-for="msg in messages" 
        :key="msg.id"
        :class="['message', msg.role]"
      >
        <div class="message-content">
          <div class="text">{{ msg.text }}</div>
          <div v-if="msg.route" class="route-badge" :class="msg.route">
            {{ getRouteLabel(msg.route) }}
          </div>
        </div>
        
        <!-- 纠错按钮（仅 KB 回答） -->
        <div v-if="msg.role === 'assistant' && msg.route === 'kb'" class="feedback-section">
          <button 
            v-if="!msg.feedbackMode"
            @click="startFeedback(msg)"
            class="btn-feedback"
          >
            答案不匹配
          </button>
          
          <!-- 纠错候选 -->
          <div v-else-if="msg.candidates" class="candidates">
            <p class="candidates-title">请选择正确的链接（1-3）：</p>
            <div 
              v-for="cand in msg.candidates" 
              :key="cand.rank"
              class="candidate"
              @click="chooseCandidate(msg, cand.rank)"
            >
              <span class="rank">{{ cand.rank }}</span>
              <div class="candidate-info">
                <div class="title">{{ cand.title }}</div>
                <div class="url">{{ cand.url }}</div>
                <div class="score">相似度: {{ (cand.score * 100).toFixed(1) }}%</div>
              </div>
            </div>
          </div>
          
          <!-- 修复成功 -->
          <div v-else-if="msg.resolved" class="resolved">
            ✅ 已记录并修复。后续类似问题将优先返回此链接。
          </div>
        </div>
      </div>
    </div>
    
    <div class="input-area">
      <input
        v-model="inputMessage"
        @keyup.enter="sendMessage"
        placeholder="请输入您的问题..."
        class="message-input"
      />
      <button @click="sendMessage" class="send-btn" :disabled="!inputMessage.trim()">
        发送
      </button>
    </div>
  </div>
</template>

<script>
import { ref, onMounted, nextTick, watch } from 'vue'
import { chatAPI } from '../api'

export default {
  name: 'ChatView',
  setup() {
    // 从 localStorage 读取 sessionId，如果没有则创建新的
    const storedSessionId = localStorage.getItem('chat_session_id')
    const sessionId = ref(storedSessionId || 'session-' + Date.now())
    
    // 如果创建了新的 sessionId，保存到 localStorage
    if (!storedSessionId) {
      localStorage.setItem('chat_session_id', sessionId.value)
    }
    
    const messages = ref([])
    const inputMessage = ref('')
    const messagesContainer = ref(null)
    const STORAGE_KEY = 'chat_messages_' + sessionId.value

    const getRouteLabel = (route) => {
      const labels = {
        'intent': '意图',
        'override': '纠错优先',
        'kb': '知识库',
        'fallback': '兜底'
      }
      return labels[route] || route
    }

    // 从 localStorage 加载聊天记录
    const loadMessages = () => {
      const stored = localStorage.getItem(STORAGE_KEY)
      if (stored) {
        try {
          const parsed = JSON.parse(stored)
          messages.value = parsed
          return true
        } catch (e) {
          console.error('加载聊天记录失败:', e)
        }
      }
      return false
    }

    // 保存聊天记录到 localStorage
    const saveMessages = () => {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(messages.value))
      } catch (e) {
        console.error('保存聊天记录失败:', e)
      }
    }

    // 清空聊天记录
    const clearHistory = () => {
      if (confirm('确定要清空所有聊天记录吗？')) {
        messages.value = []
        localStorage.removeItem(STORAGE_KEY)
        // 重新生成 sessionId
        const newSessionId = 'session-' + Date.now()
        sessionId.value = newSessionId
        localStorage.setItem('chat_session_id', newSessionId)
        // 添加欢迎消息
        messages.value.push({
          id: 0,
          role: 'assistant',
          text: '您好！我是 Atome Card 智能客服。请问有什么可以帮您？',
          route: 'fallback'
        })
        saveMessages()
      }
    }

    // 监听消息变化，自动保存
    watch(messages, () => {
      saveMessages()
    }, { deep: true })

    const sendMessage = async () => {
      const text = inputMessage.value.trim()
      if (!text) return

      // 添加用户消息
      const userMsg = {
        id: Date.now(),
        role: 'user',
        text: text
      }
      messages.value.push(userMsg)
      inputMessage.value = ''
      scrollToBottom()

      try {
        const response = await chatAPI.sendMessage(sessionId.value, text)
        const data = response.data

        messages.value.push({
          id: data.messageId,
          role: 'assistant',
          text: data.answerText,
          route: data.route,
          matched: data.matched,
          canFeedback: data.ui?.canFeedbackMismatch,
          feedbackMode: false,
          candidates: null,
          resolved: false
        })
        scrollToBottom()
      } catch (error) {
        messages.value.push({
          id: Date.now(),
          role: 'assistant',
          text: '抱歉，服务暂时不可用，请稍后重试。',
          route: 'fallback'
        })
        scrollToBottom()
      }
    }

    const startFeedback = async (msg) => {
      try {
        const response = await chatAPI.startFeedback(msg.id)
        const data = response.data

        msg.feedbackMode = true
        msg.feedbackId = data.feedbackId
        msg.candidates = data.candidates
        scrollToBottom()
      } catch (error) {
        alert('启动纠错失败: ' + error.message)
      }
    }

    const chooseCandidate = async (msg, rank) => {
      try {
        const response = await chatAPI.chooseCandidate(msg.feedbackId, rank)
        const data = response.data

        msg.candidates = null
        msg.resolved = true
        msg.resolvedUrl = data.chosenUrl
        scrollToBottom()
      } catch (error) {
        alert('选择候选失败: ' + error.message)
      }
    }

    const scrollToBottom = () => {
      nextTick(() => {
        if (messagesContainer.value) {
          messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
        }
      })
    }

    // 页面加载时
    onMounted(() => {
      // 尝试加载历史记录
      const hasHistory = loadMessages()
      
      // 如果没有历史记录，添加欢迎消息
      if (!hasHistory || messages.value.length === 0) {
        messages.value.push({
          id: 0,
          role: 'assistant',
          text: '您好！我是 Atome Card 智能客服。请问有什么可以帮您？',
          route: 'fallback'
        })
        saveMessages()
      }
    })

    return {
      sessionId,
      messages,
      inputMessage,
      messagesContainer,
      sendMessage,
      getRouteLabel,
      startFeedback,
      chooseCandidate,
      clearHistory
    }
  }
}
</script>

<style scoped>
.chat-container {
  max-width: 800px;
  margin: 20px auto;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
}

.chat-header {
  padding: 16px 20px;
  border-bottom: 1px solid #e0e0e0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-header h2 {
  font-size: 18px;
  color: #333;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.session-id {
  font-size: 12px;
  color: #999;
}

.btn-clear {
  padding: 4px 10px;
  background: transparent;
  border: 1px solid #ccc;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: #666;
  transition: all 0.2s;
}

.btn-clear:hover {
  background: #ffebee;
  border-color: #ef5350;
  color: #c62828;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message {
  max-width: 80%;
  padding: 12px 16px;
  border-radius: 12px;
  position: relative;
}

.message.user {
  align-self: flex-end;
  background: #1976d2;
  color: #fff;
}

.message.assistant {
  align-self: flex-start;
  background: #f5f5f5;
  color: #333;
}

.message-content {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.text {
  white-space: pre-wrap;
  line-height: 1.5;
}

.route-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  display: inline-block;
  width: fit-content;
}

.route-badge.intent { background: #e3f2fd; color: #1976d2; }
.route-badge.override { background: #f3e5f5; color: #7b1fa2; }
.route-badge.kb { background: #e8f5e9; color: #388e3c; }
.route-badge.fallback { background: #fff3e0; color: #f57c00; }

.feedback-section {
  margin-top: 8px;
}

.btn-feedback {
  background: transparent;
  border: 1px solid #ff9800;
  color: #ff9800;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-feedback:hover {
  background: #ff9800;
  color: #fff;
}

.candidates {
  background: #fff8e1;
  border: 1px solid #ffcc80;
  border-radius: 8px;
  padding: 12px;
  margin-top: 8px;
}

.candidates-title {
  font-size: 13px;
  color: #e65100;
  margin-bottom: 8px;
}

.candidate {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px;
  margin-bottom: 6px;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.2s;
}

.candidate:hover {
  background: #f5f5f5;
}

.rank {
  width: 24px;
  height: 24px;
  background: #1976d2;
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: bold;
  flex-shrink: 0;
}

.candidate-info {
  flex: 1;
}

.candidate-info .title {
  font-weight: 500;
  font-size: 13px;
  color: #333;
}

.candidate-info .url {
  font-size: 11px;
  color: #1976d2;
  word-break: break-all;
}

.candidate-info .score {
  font-size: 11px;
  color: #666;
  margin-top: 2px;
}

.resolved {
  background: #e8f5e9;
  border: 1px solid #a5d6a7;
  color: #2e7d32;
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 13px;
  margin-top: 8px;
}

.input-area {
  display: flex;
  gap: 8px;
  padding: 16px 20px;
  border-top: 1px solid #e0e0e0;
  background: #fafafa;
  border-radius: 0 0 8px 8px;
}

.message-input {
  flex: 1;
  padding: 10px 14px;
  border: 1px solid #ddd;
  border-radius: 20px;
  font-size: 14px;
  outline: none;
}

.message-input:focus {
  border-color: #1976d2;
}

.send-btn {
  padding: 10px 20px;
  background: #1976d2;
  color: #fff;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.2s;
}

.send-btn:hover:not(:disabled) {
  background: #1565c0;
}

.send-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}
</style>