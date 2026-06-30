import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/love',
      name: 'love',
      component: () => import('../views/LoveChatView.vue'),
    },
    {
      path: '/manus',
      name: 'manus',
      component: () => import('../views/ManusChatView.vue'),
    },
    {
      path: '/decision-engine',
      name: 'decision-engine',
      component: () => import('../views/DecisionEngineView.vue'),
    },
  ],
})

export default router
