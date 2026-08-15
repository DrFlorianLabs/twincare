<template>
  <v-container fluid class="pa-0 d-flex flex-column align-center" style="background: linear-gradient(180deg, #cae8fa 0%, #f4f9fd 40%, #f4f9fd 100%); min-height: 100vh; padding-bottom: 100px !important;">
    <div class="d-flex align-center w-100 px-4 mt-8 mb-6" style="max-width: 450px;">
      <v-btn icon="mdi-menu" variant="text" color="#37474F" size="large" @click="toggleDrawer" />
      <div class="flex-grow-1"></div>
    </div>

    <div class="avatar-container mt-4 mb-10">
      <div class="pulse-ring pulse-ring-1"></div>
      <div class="pulse-ring pulse-ring-2"></div>
      <div class="avatar-center"></div>
    </div>

    <div class="px-6 text-center mb-8" style="max-width: 350px;">
      <h2 class="text-h6 font-weight-bold" style="color: #2c3e50; line-height: 1.4; font-size: 1.15rem !important;">
        {{ questionActuelle }}
      </h2>
    </div>

    <v-row v-if="questionId && questionComplete" class="w-100 px-6" style="max-width: 380px;" justify="center">
      <template v-if="questionComplete.typeReponse === 'OUI_NON'">
        <v-col cols="6" class="pr-2 pb-2">
          <v-btn @click="envoyerReponse('OUI')" :disabled="isProcessing" variant="outlined" block rounded="xl" size="large" color="#2c3e50" class="font-weight-bold bg-white text-none" style="border-width: 1.5px; height: 50px;">Oui</v-btn>
        </v-col>
        <v-col cols="6" class="pl-2 pb-2">
          <v-btn @click="envoyerReponse('NON')" :disabled="isProcessing" variant="outlined" block rounded="xl" size="large" color="#2c3e50" class="font-weight-bold bg-white text-none" style="border-width: 1.5px; height: 50px;">Non</v-btn>
        </v-col>
        <v-col cols="12" class="pt-0">
          <v-btn @click="envoyerReponse('JE_NE_SAIS_PAS')" :disabled="isProcessing" variant="outlined" block rounded="xl" size="large" color="#2c3e50" class="font-weight-bold bg-white text-none" style="border-width: 1.5px; height: 50px;">Je ne sais pas</v-btn>
        </v-col>
      </template>

      <template v-else-if="questionComplete.typeReponse === 'ECHELLE_1_5'">
        <v-col cols="12" class="d-flex justify-space-between pb-2">
          <v-btn v-for="n in 5" :key="n" @click="envoyerReponse(n)" :disabled="isProcessing" icon variant="outlined" color="#2c3e50" class="font-weight-bold bg-white" style="border-width: 1.5px; font-size: 1.1rem;">{{ n }}</v-btn>
        </v-col>
      </template>

      <template v-else-if="questionComplete.typeReponse === 'ECHELLE_0_7'">
        <v-col cols="12" class="d-flex justify-space-between pb-2" style="flex-wrap: wrap; gap: 8px;">
          <v-btn v-for="n in [0, 1, 2, 3, 4, 5, 6, 7]" :key="n" @click="envoyerReponse(n)" :disabled="isProcessing" icon variant="outlined" color="#2c3e50" class="font-weight-bold bg-white" style="border-width: 1.5px; font-size: 1rem; width: 36px; height: 36px;">{{ n }}</v-btn>
        </v-col>
      </template>
    </v-row>

    <v-row v-else-if="compteurQuestionsSession >= 2 && !modeContinuer && !bilanTermine" class="w-100 px-6" style="max-width: 380px;" justify="center">
      <v-col cols="12" class="pb-2">
        <v-btn @click="activerModeContinuer" color="primary" variant="flat" block rounded="xl" size="large" class="font-weight-bold text-none" style="height: 50px;">Continuer (+3 questions)</v-btn>
      </v-col>
      <v-col cols="12" class="pt-0">
        <v-btn to="/documents" variant="outlined" block rounded="xl" size="large" color="#2c3e50" class="font-weight-bold bg-white text-none" style="border-width: 1.5px; height: 50px;">Passer à l'envoi de documents</v-btn>
      </v-col>
    </v-row>

    <v-row v-else-if="!questionId && (bilanTermine || compteurQuestionsSession === 0)" class="w-100 px-6" style="max-width: 380px;" justify="center">
      <v-col cols="12">
        <v-btn to="/documents" variant="outlined" block rounded="xl" size="large" color="#2c3e50" class="font-weight-bold bg-white text-none" style="border-width: 1.5px; height: 50px;">Passer à l'envoi de documents</v-btn>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { inject, onMounted, ref } from 'vue'
import { authFetch } from '../services/api'

const toggleDrawer = inject('toggleDrawer')
const questionActuelle = ref('Chargement en cours...')
const questionId = ref(null)
const questionComplete = ref(null)
const compteurQuestionsSession = ref(0)
const modeContinuer = ref(false)
const bilanTermine = ref(false)
const isProcessing = ref(false)

async function getQuestion() {
  const patientId = localStorage.getItem('patientId')
  if (!patientId) {
    questionActuelle.value = 'Votre session doit être reconnectée.'
    isProcessing.value = false
    return
  }

  try {
    const response = await authFetch(`/api/chatbot/session/patient/${patientId}?limit=1`)
    if (response.status === 204) {
      questionActuelle.value = 'Votre préparation est à jour. Merci.'
      questionId.value = null
      bilanTermine.value = true
      return
    }
    if (!response.ok) throw new Error(`Question request failed with status ${response.status}`)

    const data = await response.json()
    const question = data?.questions?.[0] || null
    if (question) {
      questionActuelle.value = question.question
      questionId.value = question.questionId
      questionComplete.value = question
    } else {
      questionActuelle.value = 'Votre préparation est à jour. Merci.'
      questionId.value = null
      bilanTermine.value = true
    }
  } catch {
    questionActuelle.value = 'Le service est momentanément indisponible.'
    questionId.value = null
  } finally {
    isProcessing.value = false
  }
}

async function envoyerReponse(choix) {
  if (!questionId.value || !questionComplete.value || isProcessing.value) return

  const patientId = localStorage.getItem('patientId')
  const dossierId = localStorage.getItem('dossierId')
  if (!patientId || !dossierId) {
    questionActuelle.value = 'Votre session doit être reconnectée.'
    return
  }

  isProcessing.value = true
  const payload = {
    questionId: questionComplete.value.questionId,
    question: questionComplete.value.question,
    categorie: questionComplete.value.categorie,
    typeReponse: questionComplete.value.typeReponse,
    reponseTexte: typeof choix === 'string' ? choix : null,
    reponseNumerique: typeof choix === 'number' ? choix : null
  }

  try {
    const response = await authFetch(`/api/chatbot/session/patient/${patientId}/dossier/${dossierId}/answer`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })

    if (!response.ok) throw new Error(`Answer request failed with status ${response.status}`)

    compteurQuestionsSession.value += 1
    if (!modeContinuer.value && compteurQuestionsSession.value >= 2) {
      questionId.value = null
      questionActuelle.value = 'Vous avez terminé cette première étape. Vous pouvez continuer si vous le souhaitez.'
      return
    }

    if (modeContinuer.value && compteurQuestionsSession.value >= 5) {
      bilanTermine.value = true
      questionId.value = null
      questionActuelle.value = 'Cette étape de préparation est terminée.'
      return
    }

    await getQuestion()
  } catch {
    questionActuelle.value = "Votre réponse n'a pas pu être enregistrée."
    questionId.value = null
  } finally {
    isProcessing.value = false
  }
}

function activerModeContinuer() {
  modeContinuer.value = true
  questionActuelle.value = 'Chargement en cours...'
  getQuestion()
}

onMounted(getQuestion)
</script>

<style scoped>
.avatar-container {
  position: relative;
  width: 250px;
  height: 250px;
  display: flex;
  justify-content: center;
  align-items: center;
}
.avatar-center {
  position: relative;
  width: 160px;
  height: 160px;
  background: linear-gradient(135deg, #7db9e8 0%, #2A93D5 100%);
  border-radius: 50%;
  z-index: 10;
  box-shadow: 0 6px 20px rgba(42, 147, 213, 0.4);
}
.pulse-ring {
  position: absolute;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.9);
  animation: pulseAnim 3s cubic-bezier(0.215, 0.61, 0.355, 1) infinite;
}
.pulse-ring-1 { width: 160px; height: 160px; animation-delay: 0s; }
.pulse-ring-2 { width: 160px; height: 160px; animation-delay: 1.5s; }
@keyframes pulseAnim {
  0% { transform: scale(1); opacity: 0.8; }
  100% { transform: scale(2.2); opacity: 0; }
}
</style>
