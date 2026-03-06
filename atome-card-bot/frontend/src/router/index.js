import { createRouter, createWebHistory } from 'vue-router'
import ChatView from '../views/ChatView.vue'
import AdminKbView from '../views/AdminKbView.vue'
import AdminIntentsView from '../views/AdminIntentsView.vue'
import AdminFeedbackView from '../views/AdminFeedbackView.vue'
import AdminOverridesView from '../views/AdminOverridesView.vue'

const routes = [
  {
    path: '/',
    name: 'Chat',
    component: ChatView
  },
  {
    path: '/admin/kb',
    name: 'AdminKb',
    component: AdminKbView
  },
  {
    path: '/admin/intents',
    name: 'AdminIntents',
    component: AdminIntentsView
  },
  {
    path: '/admin/feedback',
    name: 'AdminFeedback',
    component: AdminFeedbackView
  },
  {
    path: '/admin/overrides',
    name: 'AdminOverrides',
    component: AdminOverridesView
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router