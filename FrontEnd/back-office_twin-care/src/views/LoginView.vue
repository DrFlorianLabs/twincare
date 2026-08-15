<template>
  <v-app style="background-color: #E3F2FD;">
    <v-main class="d-flex align-center justify-center">
      <v-card class="pa-8 rounded-xl elevation-5" width="400" color="#2b2b2b" theme="dark">
        <div class="text-center mb-6">
          <v-avatar color="#156500" size="70" class="mb-4 elevation-3">
            <v-icon size="40" color="white">mdi-heart-pulse</v-icon>
          </v-avatar>
          <h1 class="text-h4 font-weight-bold" style="color: #4CAF50;">Twin Care</h1>
        </div>

        <v-alert v-if="erreur" type="error" variant="tonal" class="mb-4">{{ erreur }}</v-alert>

        <v-form @submit.prevent="soumettreConnexion" :disabled="chargement">
          <v-text-field
            v-model="email"
            label="Adresse e-mail"
            autocomplete="username"
            prepend-inner-icon="mdi-email-outline"
            variant="outlined"
            class="mb-2"
            required
          />
          <v-text-field
            v-model="motDePasse"
            label="Mot de passe"
            type="password"
            autocomplete="current-password"
            prepend-inner-icon="mdi-lock-outline"
            variant="outlined"
            class="mb-6"
            required
          />
          <v-btn
            type="submit"
            color="#156500"
            size="large"
            block
            class="text-white font-weight-bold rounded-lg"
            :loading="chargement"
            :disabled="!email.trim() || !motDePasse"
          >
            Se connecter
          </v-btn>
        </v-form>
      </v-card>
    </v-main>
  </v-app>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../services/api'

const router = useRouter()
const email = ref('')
const motDePasse = ref('')
const erreur = ref('')
const chargement = ref(false)

async function soumettreConnexion() {
  if (chargement.value) return
  chargement.value = true
  erreur.value = ''

  try {
    const response = await api.post('/api/auth/login', {
      email: email.value.trim(),
      motDePasse: motDePasse.value
    })

    const { token, role, patientId: userId } = response.data
    if (!token || !role || !userId) {
      throw new Error('Malformed authentication response')
    }

    if (role !== 'MEDECIN' && role !== 'ASSISTANT_MEDICAL' && role !== 'ADMIN') {
      erreur.value = "Ce compte n'est pas autorisé sur l'interface professionnelle."
      return
    }

    localStorage.setItem('token', token)
    localStorage.setItem('role', role)
    localStorage.setItem('userId', String(userId))

    if (role === 'MEDECIN' || role === 'ADMIN') {
      await router.push('/medecin')
    } else {
      await router.push('/assistant')
    }
  } catch (error) {
    const status = error.response?.status
    if ([400, 401, 403, 404].includes(status)) {
      erreur.value = 'Identifiants incorrects.'
    } else {
      erreur.value = 'Le service est momentanément indisponible.'
    }
  } finally {
    chargement.value = false
  }
}
</script>
