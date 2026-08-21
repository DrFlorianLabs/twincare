import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import ProfessionalWorkspaceView from '../views/ProfessionalWorkspaceView.vue'
import { professionalToken } from '../services/api'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/preparations' },
    { path: '/login', name: 'Login', component: LoginView },
    { path: '/preparations/:caseId?', name: 'Preparations', component: ProfessionalWorkspaceView },
    { path: '/medecin', redirect: '/preparations' },
    { path: '/assistant', redirect: '/preparations' },
    { path: '/:pathMatch(.*)*', redirect: '/preparations' }
  ]
})

router.beforeEach((to) => {
  const authenticated = Boolean(professionalToken())

  if (to.name === 'Login') {
    return authenticated ? { name: 'Preparations' } : true
  }
  if (!authenticated) {
    return { name: 'Login' }
  }
  return true
})

export default router
