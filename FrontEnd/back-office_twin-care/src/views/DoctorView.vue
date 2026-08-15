<template>
  <v-app style="background-color: #f8f9fa;">
    <v-app-bar color="#37474F" elevation="2">
      <v-icon class="ml-4" color="white" size="large">mdi-stethoscope</v-icon>
      <v-toolbar-title class="text-white font-weight-bold ml-2">Twin Care | Consultation</v-toolbar-title>
      <v-spacer />
      <v-chip color="white" variant="outlined" class="mr-4"><v-icon start>mdi-doctor</v-icon> Espace médecin</v-chip>
      <v-btn icon @click="deconnexion" color="white"><v-icon>mdi-logout</v-icon></v-btn>
    </v-app-bar>

    <v-main>
      <v-container fluid class="pa-6">
        <v-row class="mb-4 align-center">
          <v-col cols="12" md="8">
            <h1 class="text-h4 font-weight-bold" style="color: #37474F;">Préparations de consultation</h1>
            <p class="text-subtitle-1 text-grey-darken-1">Vue prototype avant convergence vers la vignette clinique V0.1.</p>
          </v-col>
          <v-col cols="12" md="4">
            <v-text-field v-model="recherche" label="Rechercher un patient..." prepend-inner-icon="mdi-magnify" variant="solo" density="compact" clearable hide-details bg-color="white" />
          </v-col>
        </v-row>

        <v-alert type="info" variant="tonal" class="mb-5">
          Une préparation peut être visible même sans validation humaine préalable. L'objectif est que TwinCare prépare automatiquement le cas et ne sollicite un humain qu'en cas de besoin identifié.
        </v-alert>

        <div v-if="chargement" class="text-center pa-6">
          <v-progress-circular indeterminate color="#156500" size="50" />
          <p class="mt-3 text-grey">Chargement des préparations...</p>
        </div>

        <v-alert v-else-if="dossiersFiltres.length === 0" type="info" variant="tonal" class="mb-4">Aucune préparation disponible.</v-alert>

        <v-row v-else>
          <v-col cols="12" md="4" v-for="dossier in dossiersFiltres" :key="dossier.id">
            <v-card class="rounded-xl elevation-3 border-t-lg" style="border-top: 4px solid #156500; height: 100%;">
              <v-card-title class="d-flex justify-space-between align-start pt-4 px-4 pb-0">
                <div>
                  <div class="text-h6 font-weight-bold" style="color: #37474F;">{{ dossier.patientNom }}</div>
                  <div class="text-subtitle-2 text-grey-darken-1">Préparation #{{ dossier.id }}</div>
                </div>
                <v-chip size="small" :color="couleurStatut(dossier.statut)" variant="tonal">{{ dossier.statut }}</v-chip>
              </v-card-title>

              <v-card-text class="px-4 py-3 mt-2">
                <div class="d-flex align-center mb-2">
                  <v-icon size="small" color="#156500" class="mr-2">mdi-file-document-multiple</v-icon>
                  <span>{{ dossier.nbDocuments }} document(s)</span>
                </div>
                <div class="text-caption text-grey-darken-1">Créée le {{ formaterDate(dossier.dateCreation) }}</div>
              </v-card-text>

              <v-divider />
              <v-card-actions class="px-4 py-3 bg-grey-lighten-4">
                <v-spacer />
                <v-btn color="#156500" variant="elevated" class="text-white px-4 font-weight-bold rounded-lg" prepend-icon="mdi-folder-account" @click="ouvrirConsultation(dossier)">Consulter</v-btn>
              </v-card-actions>
            </v-card>
          </v-col>
        </v-row>
      </v-container>
    </v-main>

    <v-dialog v-model="dialogConsultation" max-width="900px" scrollable>
      <v-card class="rounded-xl pa-2">
        <v-card-title class="text-h5 font-weight-bold d-flex align-center mt-2" style="color: #156500;">
          <v-icon start color="#156500" class="mr-2">mdi-folder-account</v-icon>
          Préparation de {{ consultationDossier?.patientNom }}
        </v-card-title>
        <v-divider />

        <v-card-text style="max-height: 70vh; overflow-y: auto;">
          <v-alert type="warning" variant="tonal" density="compact" class="mb-5">
            Cette vue expose encore les données du prototype historique. La prochaine tranche introduira une vignette clinique structurée, courte et sourcée.
          </v-alert>

          <h3 class="text-h6 font-weight-bold mb-3 mt-2" style="color: #37474F;">
            <v-icon start color="blue" class="mr-1">mdi-chat</v-icon> Informations recueillies
          </h3>

          <div v-if="chargementInteractions" class="text-center pa-4"><v-progress-circular indeterminate color="blue" size="30" /></div>
          <v-alert v-else-if="interactions.length === 0" type="info" variant="tonal" density="compact" class="mb-4">Aucune information conversationnelle enregistrée.</v-alert>
          <v-table v-else density="compact" class="mb-6 rounded-lg">
            <thead class="bg-blue-lighten-5"><tr><th>Catégorie</th><th>Question</th><th>Réponse</th><th>Date</th></tr></thead>
            <tbody>
              <tr v-for="inter in interactions" :key="inter.id">
                <td><v-chip size="x-small" color="blue-lighten-4">{{ inter.categorie }}</v-chip></td>
                <td>{{ inter.question }}</td>
                <td class="font-weight-bold">{{ inter.reponseTexte ?? inter.reponseNumerique ?? '-' }}</td>
                <td class="text-caption">{{ formaterDate(inter.dateInteraction) }}</td>
              </tr>
            </tbody>
          </v-table>

          <h3 class="text-h6 font-weight-bold mb-3" style="color: #37474F;">
            <v-icon start color="orange" class="mr-1">mdi-file-document-multiple</v-icon> Documents
          </h3>

          <div v-if="chargementDocs" class="text-center pa-4"><v-progress-circular indeterminate color="orange" size="30" /></div>
          <v-alert v-else-if="documents.length === 0" type="info" variant="tonal" density="compact">Aucun document trouvé.</v-alert>
          <div v-else>
            <v-card v-for="doc in documents" :key="doc.id" class="mb-3 rounded-lg elevation-1" variant="outlined">
              <v-card-text class="d-flex align-center pa-4">
                <v-icon size="32" :color="estPdf(doc.nomFichier) ? 'red' : 'blue'" class="mr-3">{{ estPdf(doc.nomFichier) ? 'mdi-file-pdf-box' : 'mdi-file-image' }}</v-icon>
                <div class="min-width-0">
                  <div class="font-weight-bold">{{ doc.type }}</div>
                  <div class="text-caption text-grey text-truncate">{{ doc.nomFichier }} · {{ doc.dateDocument }}</div>
                </div>
                <v-spacer />
                <v-chip size="x-small" :color="couleurDocument(doc.statut)" class="mr-3">{{ doc.statut }}</v-chip>
                <v-btn size="small" color="blue" variant="tonal" prepend-icon="mdi-eye" :loading="docEnOuverture === doc.id" @click="ouvrirDocument(doc)">Ouvrir</v-btn>
              </v-card-text>
            </v-card>
          </div>
        </v-card-text>

        <v-card-actions><v-spacer /><v-btn color="grey-darken-1" variant="text" @click="dialogConsultation = false">Fermer</v-btn></v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snackbar.visible" :color="snackbar.color" timeout="3500">{{ snackbar.message }}</v-snackbar>
  </v-app>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api, { openProtectedDocument } from '../services/api'

const router = useRouter()
const recherche = ref('')
const dossiers = ref([])
const chargement = ref(false)
const dialogConsultation = ref(false)
const consultationDossier = ref(null)
const interactions = ref([])
const documents = ref([])
const chargementInteractions = ref(false)
const chargementDocs = ref(false)
const docEnOuverture = ref(null)
const snackbar = ref({ visible: false, message: '', color: 'error' })

const dossiersFiltres = computed(() => {
  if (!recherche.value) return dossiers.value
  const q = recherche.value.toLowerCase()
  return dossiers.value.filter(dossier => dossier.patientNom.toLowerCase().includes(q))
})

function notifier(message, color = 'error') {
  snackbar.value = { visible: true, message, color }
}

function deconnexion() {
  localStorage.removeItem('token')
  localStorage.removeItem('role')
  localStorage.removeItem('userId')
  router.push('/login')
}

function estPdf(filename) {
  return filename?.toLowerCase().endsWith('.pdf') || false
}

function couleurStatut(statut) {
  if (statut === 'PRET_POUR_CONSULTATION') return 'success'
  if (statut === 'A_COMPLETER') return 'warning'
  return 'info'
}

function couleurDocument(statut) {
  if (statut === 'VALIDE') return 'success'
  if (statut === 'REFUSE') return 'error'
  return 'warning'
}

function formaterDate(value) {
  return value ? new Date(value).toLocaleDateString('fr-FR') : '-'
}

async function ouvrirDocument(doc) {
  docEnOuverture.value = doc.id
  try {
    await openProtectedDocument(doc.id)
  } catch {
    notifier("Le document n'a pas pu être ouvert.")
  } finally {
    docEnOuverture.value = null
  }
}

async function chargerDossiers() {
  chargement.value = true
  try {
    const response = await api.get('/api/dossiers')
    const enriched = await Promise.all(response.data.map(async dossier => {
      const patientNom = dossier.patient ? `${dossier.patient.nom} ${dossier.patient.prenom}` : 'Patient inconnu'
      let nbDocuments = 0
      try {
        const docs = await api.get(`/api/documents/dossier/${dossier.id}`)
        nbDocuments = docs.data.length
      } catch {
        nbDocuments = 0
      }
      return { ...dossier, patientNom, nbDocuments }
    }))
    dossiers.value = enriched
  } catch {
    dossiers.value = []
    notifier('Impossible de charger les préparations.')
  } finally {
    chargement.value = false
  }
}

async function ouvrirConsultation(dossier) {
  consultationDossier.value = dossier
  dialogConsultation.value = true
  interactions.value = []
  documents.value = []

  chargementInteractions.value = true
  chargementDocs.value = true

  await Promise.all([
    api.get(`/api/interactions/dossier/${dossier.id}`)
      .then(response => { interactions.value = response.data })
      .catch(() => { notifier('Impossible de charger les informations recueillies.') })
      .finally(() => { chargementInteractions.value = false }),
    api.get(`/api/documents/dossier/${dossier.id}`)
      .then(response => { documents.value = response.data })
      .catch(() => { notifier('Impossible de charger les documents.') })
      .finally(() => { chargementDocs.value = false })
  ])
}

onMounted(chargerDossiers)
</script>
