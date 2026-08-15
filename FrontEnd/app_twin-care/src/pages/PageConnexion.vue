<template>
  <v-container fluid class="fill-height pa-0" style="background: linear-gradient(180deg, #cae8fa 0%, #f4f9fd 100%);">
    <v-row justify="center" align="center" class="w-100 ma-0">
      <v-col cols="10" sm="8" md="6" class="text-center">
        <h1 class="font-weight-black mb-12" style="color: #2A93D5; font-size: 2.5rem; letter-spacing: 1px;">Twin Care</h1>

        <v-form @submit.prevent="seConnecter">
          <div class="text-left mb-1 ml-1 font-weight-medium" style="color: #37474F; font-size: 0.85rem;">Adresse mail</div>
          <v-text-field
            v-model="email"
            autocomplete="username"
            inputmode="email"
            placeholder="ex: luc@email.com"
            variant="solo"
            bg-color="white"
            rounded="lg"
            elevation="0"
            density="comfortable"
            class="mb-4"
            hide-details
            style="border: 1px solid #E0E0E0; border-radius: 8px;"
          />

          <div class="text-left mb-1 ml-1 font-weight-medium" style="color: #37474F; font-size: 0.85rem;">Mot de passe</div>
          <v-text-field
            v-model="motDePasse"
            type="password"
            autocomplete="current-password"
            variant="solo"
            bg-color="white"
            rounded="lg"
            elevation="0"
            density="comfortable"
            hide-details
            style="border: 1px solid #E0E0E0; border-radius: 8px;"
          />

          <div class="text-right mt-1 mb-6">
            <router-link to="/mot-de-passe-oublie" class="text-caption font-weight-bold" style="color: #37474F; text-decoration: underline;">Mot de passe oublié ?</router-link>
          </div>

          <v-alert v-if="erreurAffichee" type="error" variant="tonal" class="mb-4 text-caption rounded-lg" density="compact">Identifiants incorrects ou service indisponible.</v-alert>

          <v-btn
            type="submit"
            :loading="chargement"
            :disabled="chargement || !email.trim() || !motDePasse"
            color="#2A93D5"
            block
            rounded="lg"
            size="large"
            class="text-white font-weight-bold text-none"
            elevation="2"
          >
            Se connecter
          </v-btn>
        </v-form>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiUrl } from '../services/api'

const router = useRouter()
const email = ref('')
const motDePasse = ref('')
const chargement = ref(false)
const erreurAffichee = ref(false)

async function seConnecter() {
  if (chargement.value) return

  chargement.value = true
  erreurAffichee.value = false

  try {
    const response = await fetch(apiUrl('/api/auth/login'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: email.value.trim(), motDePasse: motDePasse.value })
    })

    if (!response.ok) throw new Error('Authentication failed')

    const data = await response.json()
    localStorage.setItem('token', data.token)
    localStorage.setItem('role', data.role)

    if (data.prenom) localStorage.setItem('prenom', data.prenom)
    if (data.nom) localStorage.setItem('nom', data.nom)
    if (data.email) localStorage.setItem('email', data.email)
    if (data.patientId) localStorage.setItem('patientId', String(data.patientId))
    if (data.dossierId) localStorage.setItem('dossierId', String(data.dossierId))

    await router.push('/preparation')
  } catch {
    erreurAffichee.value = true
  } finally {
    chargement.value = false
  }
}
</script>
