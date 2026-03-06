import axios from 'axios'

const API_BASE_URL = 'http://localhost:8080/api'

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Chat API
export const chatAPI = {
  sendMessage: (sessionId, message) => 
    api.post('/chat', { sessionId, message }),
  
  startFeedback: (messageId) =>
    api.post('/feedback/start', { messageId }),
  
  chooseCandidate: (feedbackId, rank) =>
    api.post('/feedback/choose', { feedbackId, rank })
}

// Admin API
export const adminAPI = {
  // KB Sources
  listKbSources: () => api.get('/admin/kb-sources'),
  createKbSource: (data) => api.post('/admin/kb-sources', data),
  updateKbSource: (id, data) => api.patch(`/admin/kb-sources/${id}`, data),
  
  // KB Articles
  listKbArticles: (sourceId) => {
    const params = sourceId ? { sourceId } : {}
    return api.get('/admin/kb-articles', { params })
  },
  
  // KB Sync
  syncKb: (sourceId, rebuild = true) => 
    api.post('/admin/kb-sync', { sourceId, rebuild }),
  
  // Intents
  listIntents: () => api.get('/admin/intents'),
  createIntent: (data) => api.post('/admin/intents', data),
  updateIntent: (id, data) => api.patch(`/admin/intents/${id}`, data),
  
  // Feedback
  listFeedback: (status) => {
    const params = status ? { status } : {}
    return api.get('/admin/feedback', { params })
  },
  
  // Overrides
  listOverrides: () => api.get('/admin/overrides'),
  updateOverride: (id, data) => api.patch(`/admin/overrides/${id}`, data)
}