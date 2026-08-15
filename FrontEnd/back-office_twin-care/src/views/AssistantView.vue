<template>
  <v-app style="background-color: #E3F2FD;">
    <v-app-bar color="#156500" elevation="2">
      <v-icon class="ml-4" color="white" size="large">mdi-heart-pulse</v-icon>
      <v-toolbar-title class="text-white font-weight-bold ml-2">Twin Care | Préparation</v-toolbar-title>
      <v-spacer />
      <v-chip color="white" variant="outlined" class="mr-4">
        <v-icon start>mdi-account-tie</v-icon> Espace assistant(e)
      </v-chip>
      <v-btn icon @click="deconnexion" color="white"><v-icon>mdi-logout</v-icon></v-btn>
    </v-app-bar>

    <v-main>
      <v-container fluid class="pa-6">
        <v-row class="mb-6 align-center">
          <v-col cols="12" md="5">
            <h1 class="text-h4 font-weight-bold" style="color: #37474F;">File de préparation</h1>
            <p class="text-subtitle-1 text-grey-darken-2">Intervenir uniquement lorsque la préparation nécessite une action humaine.</p>
          </v-col>
          <v-col cols="12" md="4">
            <v-text-field v-model="recherche" label="Rechercher un patient..." prepend-inner-icon="mdi-magnify" variant="solo" density="compact" clearable hide-details bg-color="white" />
          </v-col>
          <v-col cols="12" md="3" class="text-right">
            <v-btn size="large" color="#156500" class="text-white font-weight-bold" prepend-icon="mdi-account-plus" @click="dialogPatient = true">Créer un patient de test</v-btn>
          </v-col>
        </v-row>

        <v-alert type="warning" variant="tonal" class="mb-5">
          Le provisionnement manuel reste un mécanisme de prototype. La V0.1 cible utilisera une invitation courte liée à une préparation de consultation.
        </v-alert>

        <v-card class="rounded-xl elevation-3">
          <v-card-title class="bg-white pa-4 font-weight-bold d-flex align-center" style="color: #37474F;">
            <v-icon start color="#156500" class="mr-2">mdi-clipboard-list</v-icon>
            Patients du prototype
          </v-card-title>
          <v-divider />

          <div v-if="chargementTableau" class="text-center pa-4">
            <v-progress-circular indeterminate color="#156500" />
            <p class="mt-2 text-grey">Chargement...</p>
          </div>

          <v-table v-else>
            <thead class="bg-grey-lighten-4">
              <tr>
                <th class="font-weight-bold">Patient</th>
                <th class="font-weight-bold">Email</th>
                <th class="font-weight-bold">ID système</th>
                <th class="text-right font-weight-bold">Action</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="patientsFiltres.length === 0"><td colspan="4" class="text-center py-4 text-grey">Aucun patient trouvé.</td></tr>
              <tr v-for="patient in patientsFiltres" :key="patient.id" class="hover-row">
                <td class="font-weight-medium py-3">{{ patient.nom }} {{ patient.prenom }}</td>
                <td>{{ patient.email }}</td>
                <td><v-chip size="small">ID: {{ patient.id }}</v-chip></td>
                <td class="text-right">
                  <v-btn size="small" color="#156500" class="text-white" prepend-icon="mdi-folder-open" @click="ouvrirDossier(patient)">Voir préparation</v-btn>
                </td>
              </tr>
            </tbody>
          </v-table>
        </v-card>
      </v-container>
    </v-main>

    <v-dialog v-model="dialogPatient" max-width="600px">
      <v-card class="rounded-xl pa-2">
        <v-card-title class="text-h5 font-weight-bold d-flex align-center mt-2" style="color: #156500;">
          <v-icon start color="#156500" class="mr-2">mdi-account-plus</v-icon>
          Créer un patient de test
        </v-card-title>
        <v-card-text>
          <v-alert v-if="msgCreation" :type="typeMsg" class="mb-4" closable @click:close="msgCreation = ''">{{ msgCreation }}</v-alert>
          <v-form @submit.prevent="creerPatient" :disabled="chargementPatient">
            <v-row>
              <v-col cols="12" md="6"><v-text-field v-model="nouveau.nom" label="Nom" variant="outlined" color="#156500" required /></v-col>
              <v-col cols="12" md="6"><v-text-field v-model="nouveau.prenom" label="Prénom" variant="outlined" color="#156500" required /></v-col>
              <v-col cols="12" md="6"><v-text-field v-model="nouveau.email" label="Email" type="email" variant="outlined" color="#156500" required /></v-col>
              <v-col cols="12" md="6"><v-select v-model="nouveau.sexe" :items="['HOMME', 'FEMME']" label="Sexe" variant="outlined" color="#156500" required /></v-col>
              <v-col cols="12" md="6"><v-text-field v-model="nouveau.dateNaissance" label="Date de naissance" type="date" variant="outlined" color="#156500" required /></v-col>
              <v-col cols="12" md="6"><v-text-field v-model="nouveau.motDePasse" label="Mot de passe provisoire" type="password" autocomplete="new-password" variant="outlined" color="#156500" required /></v-col>
            </v-row>
            <div class="d-flex justify-end mt-4">
              <v-btn variant="text" color="grey-darken-1" @click="dialogPatient = false" class="mr-3">Annuler</v-btn>
              <v-btn type="submit" color="#156500" class="text-white" :loading="chargementPatient">Créer</v-btn>
            </div>
          </v-form>
        </v-card-text>
      </v-card>
    </v-dialog>

    <v-dialog v-model="dialogDossier" max-width="900px" scrollable>
      <v-card class="rounded-xl pa-2">
        <v-card-title class="text-h5 font-weight-bold d-flex align-center mt-2" style="color: #156500;">
          <v-icon start color="#156500" class="mr-2">mdi-file-document-multiple</v-icon>
          Documents de {{ patientSelectionne?.nom }} {{ patientSelectionne?.prenom }}
        </v-card-title>
        <v-divider />

        <v-card-text style="max-height: 70vh; overflow-y: auto;">
          <div v-if="chargementDocs" class="text-center pa-6">
            <v-progress-circular indeterminate color="#156500" />
            <p class="mt-2">Chargement des documents...</p>
          </div>
          <v-alert v-else-if="documents.length === 0" type="info" variant="tonal">Aucun document trouvé pour ce patient.</v-alert>

          <div v-else>
            <v-card v-for="doc in documents" :key="doc.id" class="mb-4 rounded-lg elevation-1" variant="outlined">
              <v-card-title class="d-flex align-center py-2">
                <v-icon color="blue" class="mr-2">mdi-file-document</v-icon>
                <span class="font-weight-bold">{{ doc.type || 'Non spécifié' }}</span>
                <v-chip size="x-small" :color="couleurStatut(doc.statut)" class="ml-2">{{ doc.statut }}</v-chip>
                <v-spacer />
                <span class="text-caption text-grey">{{ doc.dateDocument }}</span>
              </v-card-title>
              <v-divider />
              <v-card-text class="pa-4 d-flex align-center">
                <v-icon size="32" :color="estPdf(doc.nomFichier) ? 'red' : 'blue'" class="mr-3">{{ estPdf(doc.nomFichier) ? 'mdi-file-pdf-box' : 'mdi-file-image' }}</v-icon>
                <span class="text-body-2 text-truncate">{{ doc.nomFichier }}</span>
                <v-spacer />
                <v-btn size="small" color="blue" variant="tonal" prepend-icon="mdi-eye" :loading="docEnOuverture === doc.id" @click="ouvrirDocument(doc)">Ouvrir</v-btn>
              </v-card-text>
              <v-card-actions class="px-4 pb-3">
                <template v-if="doc.statut === 'EN_ATTENTE'">
                  <v-btn size="small" color="success" prepend-icon="mdi-check" @click="validerDocument(doc.id, 'VALIDER')">Valider</v-btn>
                  <v-btn size="small" color="error" prepend-icon="mdi-close" @click="validerDocument(doc.id, 'REFUSER')">Refuser</v-btn>
                </template>
                <v-spacer />
                <v-btn size="small" color="grey-darken-1" variant="tonal" prepend-icon="mdi-delete" @click="supprimerDocument(doc.id)">Supprimer le document</v-btn>
              </v-card-actions>
            </v-card>
          </div>
        </v-card-text>
        <v-card-actions><v-spacer /><v-btn color="grey-darken-1" variant="text" @click="dialogDossier = false">Fermer</v-btn></v-card-actions>
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
const patients = ref([])
const chargementTableau = ref(false)
const dialogPatient = ref(false)
const chargementPatient = ref(false)
const msgCreation = ref('')
const typeMsg = ref('success')
const dialogDossier = ref(false)
const patientSelectionne = ref(null)
const documents = ref([])
const chargementDocs = ref(false)
const dossierId = ref(null)
const docEnOuverture = ref(null)
const snackbar = ref({ visible: false, message: '', color: 'error' })
const nouveau = ref({ nom: '', prenom: '', dateNaissance: '', email: '', motDePasse: '', role: 'PATIENT', sexe: null })

const patientsFiltres = computed(() => {
  if (!recherche.value) return patients.value
  const q = recherche.value.toLowerCase()
  return patients.value.filter(p => `${p.nom} ${p.prenom}`.toLowerCase().includes(q) || p.email.toLowerCase().includes(q))
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
  if (statut === 'VALIDE') return 'green'
  if (statut === 'REFUSE') return 'red'
  return 'orange'
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

async function chargerPatients() {
  chargementTableau.value = true
  try {
    const response = await api.get('/api/users')
    patients.value = response.data.filter(user => user.role === 'PATIENT')
  } catch {
    patients.value = []
    notifier('Impossible de charger les patients.')
  } finally {
    chargementTableau.value = false
  }
}

async function creerPatient() {
  chargementPatient.value = true
  msgCreation.value = ''
  try {
    await api.post('/api/auth/register', nouveau.value)
    typeMsg.value = 'success'
    msgCreation.value = 'Patient de test créé.'
    await chargerPatients()
    nouveau.value = { nom: '', prenom: '', dateNaissance: '', email: '', motDePasse: '', role: 'PATIENT', sexe: null }
  } catch {
    typeMsg.value = 'error'
    msgCreation.value = "La création n'a pas abouti."
  } finally {
    chargementPatient.value = false
  }
}

async function ouvrirDossier(patient) {
  patientSelectionne.value = patient
  dialogDossier.value = true
  chargementDocs.value = true
  documents.value = []

  try {
    const dossierResponse = await api.get(`/api/dossiers/patient/${patient.id}`)
    dossierId.value = dossierResponse.data.id
    const response = await api.get(`/api/documents/dossier/${dossierId.value}`)
    documents.value = response.data
  } catch {
    notifier('Impossible de charger la préparation.')
  } finally {
    chargementDocs.value = false
  }
}

async function validerDocument(documentId, decision) {
  try {
    const assistantId = Number(localStorage.getItem('userId'))
    await api.post(`/api/validations/document/${documentId}`, {
      decision,
      commentaire: 'Traité par un professionnel',
      assistantMedicalId: assistantId
    })
    const response = await api.get(`/api/documents/dossier/${dossierId.value}`)
    documents.value = response.data
  } catch {
    notifier("La validation n'a pas abouti.")
  }
}

async function supprimerDocument(documentId) {
  if (!confirm('Supprimer ce document ?')) return
  try {
    await api.delete(`/api/documents/${documentId}`)
    const response = await api.get(`/api/documents/dossier/${dossierId.value}`)
    documents.value = response.data
  } catch {
    notifier("Le document n'a pas pu être supprimé.")
  }
}

onMounted(chargerPatients)
</script>

<style scoped>
.hover-row:hover {
  background-color: #f5f5f5;
  transition: background-color 0.2s;
}
</style>
