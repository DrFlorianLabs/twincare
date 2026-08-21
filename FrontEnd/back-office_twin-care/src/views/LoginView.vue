<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { clearProfessionalSession, storeProfessionalSession } from '../services/api'
import { authenticateProfessional } from '../services/preparationService'

const route = useRoute()
const router = useRouter()
const form = ref(null)
const email = ref('')
const password = ref('')
const showPassword = ref(false)
const loading = ref(false)
const errorMessage = ref('')

clearProfessionalSession()

async function submit() {
  errorMessage.value = ''
  const validation = await form.value?.validate()
  if (!validation?.valid) return

  loading.value = true
  try {
    const response = await authenticateProfessional(email.value.trim(), password.value)
    storeProfessionalSession(response?.token)
    password.value = ''
    await router.replace({ name: 'Preparations' })
  } catch {
    clearProfessionalSession()
    password.value = ''
    errorMessage.value = 'Connexion impossible. Vérifiez vos identifiants et réessayez.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <v-main class="login-background">
    <v-container class="fill-height py-10">
      <v-row justify="center" align="center">
        <v-col cols="12" sm="9" md="6" lg="4">
          <div class="brand-mark mb-5" aria-hidden="true">TC</div>
          <p class="text-overline text-primary mb-1">TwinCare Consultation</p>
          <h1 class="text-h4 font-weight-bold mb-2">Préparer avant de recevoir</h1>
          <p class="text-body-1 text-medium-emphasis mb-7">
            L’essentiel de la demande, des documents et des actions à mener avant la consultation.
          </p>

          <v-alert
            v-if="route.query.expired"
            type="info"
            variant="tonal"
            class="mb-4"
          >
            Votre session est terminée. Reconnectez-vous pour continuer.
          </v-alert>

          <v-card rounded="xl" elevation="2">
            <v-card-title class="px-6 pt-6">Accès professionnel</v-card-title>
            <v-card-text class="px-6 pb-6">
              <v-alert
                v-if="errorMessage"
                type="error"
                variant="tonal"
                class="mb-4"
                role="alert"
              >
                {{ errorMessage }}
              </v-alert>

              <v-form ref="form" @submit.prevent="submit">
                <v-text-field
                  v-model="email"
                  label="Adresse e-mail"
                  type="email"
                  autocomplete="username"
                  prepend-inner-icon="mdi-email-outline"
                  :rules="[
                    value => Boolean(value?.trim()) || 'Adresse e-mail requise',
                    value => /.+@.+\..+/.test(value) || 'Adresse e-mail invalide'
                  ]"
                  :disabled="loading"
                />
                <v-text-field
                  v-model="password"
                  label="Mot de passe"
                  :type="showPassword ? 'text' : 'password'"
                  autocomplete="current-password"
                  prepend-inner-icon="mdi-lock-outline"
                  :append-inner-icon="showPassword ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
                  :rules="[value => Boolean(value) || 'Mot de passe requis']"
                  :disabled="loading"
                  @click:append-inner="showPassword = !showPassword"
                />
                <v-btn
                  type="submit"
                  color="primary"
                  size="large"
                  block
                  :loading="loading"
                  class="mt-2"
                >
                  Se connecter
                </v-btn>
              </v-form>
            </v-card-text>
          </v-card>

          <p class="text-caption text-medium-emphasis mt-5">
            Environnement de développement : aucune donnée de patient réel ne doit être utilisée.
          </p>
        </v-col>
      </v-row>
    </v-container>
  </v-main>
</template>

<style scoped>
.login-background {
  background:
    radial-gradient(circle at 10% 10%, rgba(22, 92, 74, 0.13), transparent 34rem),
    #f4f7f5;
}

.brand-mark {
  display: grid;
  width: 3.25rem;
  height: 3.25rem;
  place-items: center;
  border-radius: 1rem;
  color: white;
  background: #165c4a;
  font-weight: 800;
  letter-spacing: 0.04em;
}
</style>
