<template>
  <v-app>
    <v-navigation-drawer v-model="drawer" temporary width="300">
      <div class="pa-6" style="background-color: #2A93D5; color: white; height: 180px;">
        <v-avatar size="70" color="white" class="mb-4">
          <v-img v-if="userPhoto" :src="userPhoto" cover />
          <v-icon v-else size="55" color="#B0BEC5">mdi-account</v-icon>
        </v-avatar>
        <div class="text-h5 font-weight-bold text-capitalize" style="line-height: 1.2;">Bonjour<br>{{ userName }}</div>
      </div>

      <v-list class="pa-2 mt-2">
        <v-list-item to="/preparation" prepend-icon="mdi-forum-outline" title="Préparer ma consultation" class="py-3">
          <template #append><v-icon size="small">mdi-chevron-right</v-icon></template>
        </v-list-item>
        <v-list-item to="/mes-documents" prepend-icon="mdi-file-document-outline" title="Mes documents" class="py-3">
          <template #append><v-icon size="small">mdi-chevron-right</v-icon></template>
        </v-list-item>
        <v-list-item to="/profil" prepend-icon="mdi-account-outline" title="Mon profil" class="py-3">
          <template #append><v-icon size="small">mdi-chevron-right</v-icon></template>
        </v-list-item>
        <v-list-item to="/confidentialite" prepend-icon="mdi-lock-outline" title="Confidentialité" class="py-3">
          <template #append><v-icon size="small">mdi-chevron-right</v-icon></template>
        </v-list-item>
      </v-list>

      <template #append>
        <div class="pa-4">
          <v-list-item prepend-icon="mdi-logout" title="Déconnexion" class="mb-4" @click="seDeconnecter" />
          <v-btn block variant="outlined" color="red" rounded="lg" class="text-none font-weight-bold" href="tel:15">Urgence 15</v-btn>
        </div>
      </template>
    </v-navigation-drawer>

    <v-main><router-view /></v-main>
    <BarreNavigation v-if="!isLoginPage" />
  </v-app>
</template>

<script setup>
import { computed, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BarreNavigation from './components/BarreNavigation.vue'

const drawer = ref(false)
const route = useRoute()
const router = useRouter()
const userName = ref('Patient')
const userPhoto = ref(null)

const isLoginPage = computed(() => route.path === '/' || route.path === '/mot-de-passe-oublie')

watch(drawer, (isOpen) => {
  if (!isOpen) return
  const prenom = localStorage.getItem('prenom')
  const nom = localStorage.getItem('nom')
  if (prenom) userName.value = prenom + (nom ? ` ${nom.charAt(0)}.` : '')
})

function toggleDrawer() {
  drawer.value = !drawer.value
}
provide('toggleDrawer', toggleDrawer)

function seDeconnecter() {
  localStorage.clear()
  drawer.value = false
  router.push('/')
}
</script>

<style>
body {
  background-color: #f4f9fd;
}
</style>
