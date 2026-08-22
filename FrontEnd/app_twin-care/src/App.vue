<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'

import { createPatientAccessClient } from './services/patientAccessService.js'
import {
  createPatientPreparationJourney,
  PatientJourneyPhase,
} from './usecases/patientPreparationJourney.js'

type InvitationCapture =
  | { kind: 'NONE' }
  | { kind: 'INVALID' }
  | { kind: 'TOKEN'; token: string | null }

const props = defineProps<{ invitationCapture: InvitationCapture }>()

const journey = createPatientPreparationJourney(createPatientAccessClient())
const state = journey.state
const statusHeading = ref<HTMLElement | null>(null)
const formError = ref<HTMLElement | null>(null)
const remainingCharacters = computed(() => 4_000 - state.draft.length)
const canSave = computed(() => journey.canSave())

onMounted(() => journey.start(props.invitationCapture))

watch(
  () => state.phase,
  async () => {
    await nextTick()
    statusHeading.value?.focus()
  },
)

watch(
  () => state.formError,
  async (message) => {
    if (!message) return
    await nextTick()
    formError.value?.focus()
  },
)
</script>

<template>
  <div class="page-shell">
    <header class="brand" aria-label="TwinCare">
      <span class="brand-mark" aria-hidden="true">T</span>
      <span>TwinCare</span>
    </header>

    <main class="preparation-card">
      <p class="eyebrow">Avant votre consultation</p>

      <section v-if="state.phase === PatientJourneyPhase.LOADING" aria-live="polite">
        <h1 ref="statusHeading" tabindex="-1">Préparation en cours d’ouverture</h1>
        <p>Nous vérifions votre lien sécurisé.</p>
        <p class="loading-text" role="status">Veuillez patienter…</p>
      </section>

      <section v-else-if="state.phase === PatientJourneyPhase.LINK_ERROR">
        <h1 ref="statusHeading" tabindex="-1">Ce lien ne peut pas être utilisé</h1>
        <p>
          Il est peut-être expiré ou a déjà été utilisé. Demandez simplement un nouveau lien au
          cabinet.
        </p>
      </section>

      <section v-else-if="state.phase === PatientJourneyPhase.SESSION_EXPIRED">
        <h1 ref="statusHeading" tabindex="-1">Votre accès a expiré</h1>
        <p>Pour protéger vos informations, demandez un nouveau lien au cabinet.</p>
      </section>

      <section v-else-if="state.phase === PatientJourneyPhase.NETWORK_ERROR">
        <h1 ref="statusHeading" tabindex="-1">La connexion n’a pas abouti</h1>
        <p>Vérifiez votre connexion internet, puis réessayez.</p>
        <button class="primary-button" type="button" @click="journey.retryConnection">
          Réessayer
        </button>
      </section>

      <section v-else-if="state.phase === PatientJourneyPhase.SAVED">
        <h1 ref="statusHeading" tabindex="-1">Votre demande est enregistrée</h1>
        <p>Elle sera disponible pour préparer votre consultation.</p>
        <div class="saved-answer" aria-label="Votre demande enregistrée">
          {{ state.savedReason }}
        </div>
        <button class="secondary-button" type="button" @click="journey.editReason">
          Modifier ma demande
        </button>
      </section>

      <form v-else @submit.prevent="journey.saveReason">
        <h1 ref="statusHeading" tabindex="-1">Préparer ma consultation</h1>
        <label for="consultation-reason">
          En quelques mots, qu’aimeriez-vous aborder pendant cette consultation&nbsp;?
        </label>
        <p id="reason-help" class="field-help">Une ou deux phrases suffisent.</p>
        <textarea
          id="consultation-reason"
          v-model="state.draft"
          name="consultationReason"
          rows="7"
          maxlength="4000"
          autocomplete="off"
          aria-describedby="reason-help reason-counter"
          :aria-invalid="Boolean(state.formError)"
          :disabled="state.saving"
        />
        <p id="reason-counter" class="character-count">
          {{ remainingCharacters.toLocaleString('fr-FR') }} caractères restants
        </p>
        <p
          v-if="state.formError"
          ref="formError"
          class="error-message"
          role="alert"
          tabindex="-1"
        >
          {{ state.formError }}
        </p>
        <div class="actions">
          <button
            v-if="state.savedReason"
            class="text-button"
            type="button"
            :disabled="state.saving"
            @click="journey.cancelEdit"
          >
            Annuler
          </button>
          <button class="primary-button" type="submit" :disabled="!canSave">
            {{ state.saving ? 'Enregistrement…' : 'Enregistrer' }}
          </button>
        </div>
      </form>
    </main>

    <footer>
      Vos informations restent limitées à la préparation de cette consultation.
    </footer>
  </div>
</template>
