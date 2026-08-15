import { createRouter, createWebHistory } from 'vue-router'

import PageConnexion from '../pages/PageConnexion.vue'
import PageMotDePasseOublie from '../pages/PageMotDePasseOublie.vue'
import PageDocuments from '../pages/PageDocuments.vue'
import PageChatbot from '../pages/PageChatbot.vue'
import MesDocuments from '../pages/MesDocuments.vue'
import PageProfil from '../pages/PageProfil.vue'
import PageConfidentialite from '../pages/PageConfidentialite.vue'

const routes = [
  { path: '/', component: PageConnexion },
  { path: '/mot-de-passe-oublie', component: PageMotDePasseOublie },
  { path: '/preparation', component: PageChatbot, alias: '/chatbot' },
  { path: '/documents', component: PageDocuments },
  { path: '/mes-documents', component: MesDocuments },
  { path: '/profil', component: PageProfil },
  { path: '/confidentialite', component: PageConfidentialite },

  // Historical prototype modules are intentionally outside the V0.1 runtime
  // navigation. Source files remain in the repository as project history.
  { path: '/parcours', redirect: '/preparation' },
  { path: '/environnement', redirect: '/preparation' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
