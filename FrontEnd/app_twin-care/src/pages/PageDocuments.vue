<template>
  <v-container fluid class="pa-0 d-flex flex-column align-center" style="background: linear-gradient(180deg, #cae8fa 0%, #f4f9fd 40%, #f4f9fd 100%); min-height: 100vh; padding-bottom: 100px !important;">
    <div class="d-flex align-center w-100 px-4 mt-8 mb-6" style="max-width: 450px;">
      <v-btn icon="mdi-menu" variant="text" color="#37474F" size="large" @click="toggleDrawer" />
      <h2 class="text-center flex-grow-1 text-white font-weight-black" style="font-size: 1.25rem; letter-spacing: 0.5px; text-shadow: 0 2px 4px rgba(0,0,0,0.15);">
        ENVOI DES DOCUMENTS
      </h2>
      <div style="width: 48px;"></div>
    </div>

    <v-row class="w-100 px-6 mt-4" style="max-width: 380px;" justify="center">
      <v-col cols="12" class="pb-8">
        <v-card
          @click="actionUpload('camera')"
          class="d-flex flex-column align-center justify-center pa-0"
          elevation="0"
          color="transparent"
          v-ripple
          style="cursor: pointer;"
        >
          <img :src="imageCamera" alt="Prendre un document en photo" style="width: 100%; max-width: 300px; object-fit: contain;" />
        </v-card>
      </v-col>

      <v-col cols="12" class="pb-5">
        <v-select
          v-model="documentType"
          :items="typeOptions"
          item-title="titre"
          item-value="valeur"
          label="Type de document"
          placeholder="Veuillez choisir une option"
          variant="outlined"
          color="#2c3e50"
          bg-color="white"
          rounded="xl"
          hide-details
          class="font-weight-medium"
        />
      </v-col>

      <v-col cols="12" class="pt-0">
        <v-btn
          variant="outlined"
          color="#2c3e50"
          block
          rounded="xl"
          size="large"
          class="text-none font-weight-bold bg-white"
          style="border-width: 1.5px; height: 50px;"
          @click="actionUpload('file')"
          :loading="isUploading"
        >
          Parcourir mes fichiers
        </v-btn>
      </v-col>
    </v-row>

    <input type="file" accept="image/jpeg,image/png" capture="environment" ref="cameraInput" class="d-none" @change="gererFichierSelectionne" />
    <input type="file" accept="image/jpeg,image/png,application/pdf" ref="fileInput" class="d-none" @change="gererFichierSelectionne" />

    <v-snackbar v-model="showSnackbar" :color="snackbarColor" elevation="6" rounded="pill" timeout="3000" location="bottom" class="mb-16">
      <div class="d-flex align-center w-100 px-1">
        <v-icon :icon="snackbarIcon" color="white" class="mr-3" size="24" />
        <span class="text-body-2 text-white font-weight-medium">{{ snackbarText }}</span>
        <v-spacer />
        <v-btn variant="tonal" color="white" size="small" class="text-none font-weight-bold rounded-pill ml-3" @click="showSnackbar = false">
          Fermer
        </v-btn>
      </div>
    </v-snackbar>
  </v-container>
</template>

<script setup>
import { inject, ref } from 'vue'
import imageCamera from '../assets/camera.png'
import { authFetch } from '../services/api'

const toggleDrawer = inject('toggleDrawer')
const showSnackbar = ref(false)
const snackbarText = ref('')
const snackbarColor = ref('#2c3e50')
const snackbarIcon = ref('mdi-check-circle')
const isUploading = ref(false)
const cameraInput = ref(null)
const fileInput = ref(null)
const documentType = ref(null)

const typeOptions = [
  { titre: 'Ordonnance', valeur: 'ORDONNANCE' },
  { titre: 'Résultats Biologie', valeur: 'BIOLOGIE' },
  { titre: 'Imagerie (Radio, IRM...)', valeur: 'IMAGERIE' },
  { titre: 'Courrier médical', valeur: 'COURRIER' },
  { titre: 'Certificat médical', valeur: 'CERTIFICAT' },
  { titre: 'Formulaire CERFA', valeur: 'CERFA' },
  { titre: 'Autre document', valeur: 'AUTRE' }
]

function actionUpload(source) {
  if (!documentType.value) {
    afficherNotification("Veuillez choisir un type de document avant d'importer.", '#E53935', 'mdi-alert-circle')
    return
  }

  if (source === 'camera') cameraInput.value?.click()
  if (source === 'file') fileInput.value?.click()
}

function afficherNotification(message, couleur = '#2c3e50', icone = 'mdi-check-circle') {
  snackbarText.value = message
  snackbarColor.value = couleur
  snackbarIcon.value = icone
  showSnackbar.value = true
}

function gererFichierSelectionne(event) {
  const file = event.target.files?.[0]
  if (!file) return
  envoyerDocument(file)
  event.target.value = ''
}

async function envoyerDocument(fichierChoisi) {
  const dossierId = localStorage.getItem('dossierId')
  if (!dossierId) {
    afficherNotification("Votre session n'est pas associée à une consultation. Reconnectez-vous.", '#E53935', 'mdi-alert-circle')
    return
  }

  isUploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', fichierChoisi)
    formData.append('type', documentType.value)
    formData.append('dateDocument', new Date().toISOString().split('T')[0])

    const response = await authFetch(`/api/documents/upload/dossier/${dossierId}`, {
      method: 'POST',
      body: formData
    })

    if (!response.ok) {
      throw new Error(`Upload failed with status ${response.status}`)
    }

    documentType.value = null
    afficherNotification('Document envoyé avec succès.', '#4CAF50', 'mdi-check-circle')
  } catch {
    afficherNotification("Le document n'a pas pu être envoyé.", '#E53935', 'mdi-alert-circle')
  } finally {
    isUploading.value = false
  }
}
</script>
