<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  buildClinicalExport, buildClinicalVignette, canReviewClinicalReason, certaintyLabel,
  clinicalDate, clinicalScopeKey, clinicalSnapshotIsConsistent,
  currentClinicalReason, historicalClinicalReasons, provenanceLabel, reviewLabel
} from '../services/clinicalContextPresentation.js'
import {
  createClinicalScopeTracker, getClinicalContext, prepareClinicalExport, reviewClinicalFact
} from '../services/clinicalContextService.js'

const props = defineProps({
  organizationId: { type: String, required: true },
  actorUserId: { type: Number, required: true },
  capabilities: { type: Array, default: () => [] },
  preparation: { type: Object, required: true }
})
const emit = defineEmits(['availability', 'refresh', 'access-error'])
const clinicalContext = ref(null)
const loading = ref(false)
const busy = ref('')
const error = ref('')
const feedback = ref('')
const manualCopy = ref('')
const scope = createClinicalScopeTracker()
let controller
const scopeKey = computed(() => clinicalScopeKey(
  props.organizationId, props.preparation?.caseId, props.actorUserId, props.capabilities
))
const valid = computed(() => Boolean(scopeKey.value)
  && clinicalSnapshotIsConsistent(clinicalContext.value, props.preparation))
const reason = computed(() => valid.value ? currentClinicalReason(clinicalContext.value) : null)
const history = computed(() => valid.value ? historicalClinicalReasons(clinicalContext.value) : [])
const reviewable = computed(() => valid.value
  && canReviewClinicalReason(clinicalContext.value, props.preparation, props.capabilities))

function cancelled(exception) {
  return exception?.code === 'ERR_CANCELED' || exception?.name === 'CanceledError'
}

function handleAccessError(exception) {
  if ([401, 403, 404].includes(exception?.response?.status)) {
    emit('access-error', exception)
    return true
  }
  return false
}

async function load() {
  controller?.abort()
  controller = new AbortController()
  scope.reset(scopeKey.value)
  const ticket = scope.capture()
  clinicalContext.value = null
  error.value = ''
  feedback.value = ''
  manualCopy.value = ''
  busy.value = ''
  emit('availability', false)
  if (!scopeKey.value) {
    loading.value = false
    error.value = 'Le contexte professionnel doit être rechargé.'
    return
  }
  loading.value = true
  try {
    const result = await getClinicalContext(props.organizationId, props.preparation.caseId, {
      signal: controller.signal
    })
    if (!scope.applies(ticket)) return
    if (!clinicalSnapshotIsConsistent(result, props.preparation)) {
      error.value = 'La préparation a changé ou ses informations ne concordent pas. Actualisez la vue avant de poursuivre.'
      return
    }
    clinicalContext.value = result
    emit('availability', result.readiness === 'COMPLETE')
  } catch (exception) {
    if (cancelled(exception) || !scope.applies(ticket)) return
    if (handleAccessError(exception)) return
    error.value = 'Les informations cliniques sont momentanément indisponibles.'
  } finally {
    if (scope.applies(ticket)) loading.value = false
  }
}

async function review(decision) {
  if (!reviewable.value || busy.value) return
  const ticket = scope.capture()
  const factId = reason.value.id
  busy.value = decision
  feedback.value = ''
  error.value = ''
  manualCopy.value = ''
  try {
    await reviewClinicalFact(
      props.organizationId, props.preparation.caseId, factId, decision,
      { signal: controller.signal }
    )
    if (!scope.applies(ticket)) return
    clinicalContext.value = null
    emit('availability', false)
    emit('refresh', decision)
  } catch (exception) {
    if (cancelled(exception) || !scope.applies(ticket)) return
    if (handleAccessError(exception)) return
    if (exception?.response?.status === 409) {
      clinicalContext.value = null
      emit('availability', false)
      emit('refresh', 'CONFLICT')
    } else {
      error.value = 'La relecture n’a pas pu être confirmée. Actualisez la vue avant de réessayer.'
      clinicalContext.value = null
      emit('availability', false)
    }
  } finally {
    if (scope.applies(ticket)) busy.value = ''
  }
}

async function exportSnapshot(format) {
  if (!valid.value || busy.value) return
  const ticket = scope.capture()
  const organizationId = props.organizationId
  const caseId = props.preparation.caseId
  const displayed = { context: clinicalContext.value, preparation: props.preparation }
  busy.value = format
  feedback.value = ''
  error.value = ''
  manualCopy.value = ''
  try {
    const fresh = await prepareClinicalExport({
      organizationId, caseId, displayed,
      stillApplies: () => scope.applies(ticket),
      signal: controller.signal
    })
    if (!fresh || !scope.applies(ticket)) return
    if (format === 'COPY') {
      const text = buildClinicalVignette(fresh.context, fresh.preparation)
      try {
        if (!globalThis.navigator?.clipboard?.writeText) throw new Error('Clipboard unavailable')
        await globalThis.navigator.clipboard.writeText(text)
        if (scope.applies(ticket)) feedback.value = 'Vignette copiée. Vérifiez la référence patient avant de la coller dans votre logiciel.'
      } catch {
        if (!scope.applies(ticket)) return
        manualCopy.value = text
        feedback.value = 'La copie automatique est indisponible. Sélectionnez le texte ci-dessous pour le copier.'
      }
    } else {
      const payload = buildClinicalExport(fresh.context, fresh.preparation, organizationId)
      const url = URL.createObjectURL(new Blob([JSON.stringify(payload, null, 2)], {
        type: 'application/json;charset=utf-8'
      }))
      try {
        const link = document.createElement('a')
        link.href = url
        link.download = 'twincare-preparation.json'
        document.body.appendChild(link)
        link.click()
        link.remove()
      } finally {
        window.setTimeout(() => URL.revokeObjectURL(url), 1000)
      }
      feedback.value = 'Export téléchargé avec les tâches, les métadonnées documentaires et l’historique de relecture.'
    }
  } catch (exception) {
    if (cancelled(exception) || !scope.applies(ticket)) return
    if (handleAccessError(exception)) return
    if (exception?.code === 'CLINICAL_SNAPSHOT_CHANGED') {
      clinicalContext.value = null
      emit('availability', false)
      emit('refresh', 'EXPORT_CHANGED')
    } else {
      error.value = 'L’export n’a pas pu être vérifié. Actualisez la vue avant de réessayer.'
    }
  } finally {
    if (scope.applies(ticket)) busy.value = ''
  }
}

watch([() => props.preparation, scopeKey], load, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => {
  scope.reset()
  controller?.abort()
  clinicalContext.value = null
  manualCopy.value = ''
})
</script>

<template>
  <v-card rounded="xl" elevation="1" class="h-100" :aria-busy="loading || Boolean(busy)">
    <v-card-title class="px-5 pt-5">Vignette clinique</v-card-title>
    <v-card-text class="px-5 pb-5">
      <v-skeleton-loader v-if="loading" type="paragraph" />
      <v-alert v-if="error" type="warning" variant="tonal" class="mb-4" role="alert">
        {{ error }}
        <v-btn size="small" variant="text" :disabled="Boolean(busy)" @click="emit('refresh', 'RELOAD')">
          Actualiser la préparation
        </v-btn>
      </v-alert>

      <template v-if="valid">
        <v-alert v-if="!reason" type="info" variant="tonal">
          Le patient n’a pas encore renseigné son motif.
        </v-alert>
        <template v-else>
          <div class="d-flex flex-wrap ga-2 mb-3">
            <v-chip size="small" variant="tonal">{{ provenanceLabel(reason.provenance.type) }}</v-chip>
            <v-chip size="small" variant="tonal"
              :color="reason.review.status === 'REJECTED' ? 'error' : reason.review.status === 'CONFIRMED' ? 'success' : 'info'">
              {{ reviewLabel(reason.review.status) }}
            </v-chip>
          </div>
          <template v-if="reason.review.status === 'REJECTED'">
            <v-alert type="warning" variant="tonal" class="mb-3">
              Motif à corriger. Le contenu rejeté est exclu de la vignette copiée.
            </v-alert>
            <details class="mb-3">
              <summary>Voir le contenu rejeté</summary>
              <p class="clinical-text mt-2">{{ reason.value.text }}</p>
            </details>
          </template>
          <p v-else class="clinical-text">{{ reason.value.text }}</p>
          <dl class="clinical-metadata mt-4">
            <dt>Recueilli le</dt><dd>{{ clinicalDate(reason.temporal.recordedAt) }}</dd>
            <dt>Certitude de la source</dt><dd>{{ certaintyLabel(reason.certainty) }}</dd>
            <template v-if="reason.temporal.effectiveFrom">
              <dt>Début rapporté</dt><dd>{{ clinicalDate(reason.temporal.effectiveFrom) }}</dd>
            </template>
            <template v-if="reason.temporal.effectiveTo">
              <dt>Fin rapportée</dt><dd>{{ clinicalDate(reason.temporal.effectiveTo) }}</dd>
            </template>
            <template v-if="reason.review.reviewedAt">
              <dt>Relu le</dt><dd>{{ clinicalDate(reason.review.reviewedAt) }}</dd>
            </template>
          </dl>
          <div v-if="reviewable" class="mt-4">
            <p class="text-caption text-medium-emphasis mb-2">
              La relecture confirme ou rejette cette information ; elle ne vaut pas diagnostic.
            </p>
            <div class="d-flex flex-wrap ga-2">
              <v-btn size="small" variant="tonal" :disabled="Boolean(busy)" :loading="busy === 'CONFIRM'"
                @click="review('CONFIRM')">Confirmer le motif</v-btn>
              <v-btn size="small" variant="text" color="error" :disabled="Boolean(busy)" :loading="busy === 'REJECT'"
                @click="review('REJECT')">Rejeter le motif</v-btn>
            </div>
          </div>
        </template>

        <details v-if="history.length" class="mt-5">
          <summary>Historique du motif ({{ history.length }})</summary>
          <article v-for="fact in history" :key="fact.id" class="history-entry mt-3">
            <p class="text-caption font-weight-bold">Ancienne version remplacée le {{ clinicalDate(fact.lifecycle.supersededAt) }}</p>
            <p class="clinical-text mt-2">{{ fact.value.text }}</p>
            <p class="text-caption mt-2">
              {{ provenanceLabel(fact.provenance.type) }} · {{ clinicalDate(fact.temporal.recordedAt) }}<br>
              {{ certaintyLabel(fact.certainty) }} · {{ reviewLabel(fact.review.status) }}
              <span v-if="fact.review.reviewedAt"> · {{ clinicalDate(fact.review.reviewedAt) }}</span>
            </p>
          </article>
        </details>

        <v-divider class="my-5" />
        <div class="d-flex flex-wrap ga-2">
          <v-btn color="primary" prepend-icon="mdi-content-copy" :disabled="Boolean(busy)"
            :loading="busy === 'COPY'" @click="exportSnapshot('COPY')">Copier la vignette</v-btn>
          <v-btn variant="text" prepend-icon="mdi-download-outline" :disabled="Boolean(busy)"
            :loading="busy === 'JSON'" @click="exportSnapshot('JSON')">Exporter en JSON</v-btn>
        </div>
        <p class="text-caption text-medium-emphasis mt-3">
          La copie inclut les tâches et les documents transmis. Le fichier JSON conserve aussi les anciennes versions et les rejets.
        </p>
      </template>

      <p v-if="feedback" role="status" aria-live="polite" class="text-body-2 mt-4">{{ feedback }}</p>
      <v-textarea v-if="manualCopy" :model-value="manualCopy" label="Vignette à copier manuellement"
        readonly auto-grow rows="6" class="mt-3" @focus="$event.target.select()" />
    </v-card-text>
  </v-card>
</template>

<style scoped>
.clinical-text { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.65; }
.clinical-metadata { font-size: .82rem; display: grid; grid-template-columns: minmax(7rem, 1fr) 2fr; gap: .35rem .75rem; }
.clinical-metadata dt { color: rgb(var(--v-theme-on-surface), .7); }
.clinical-metadata dd { margin: 0; overflow-wrap: anywhere; }
.history-entry { padding: .8rem; background: rgba(var(--v-theme-on-surface), .04); border-radius: .5rem; }
summary { cursor: pointer; line-height: 1.6; }
summary:focus-visible { outline: 2px solid rgb(var(--v-theme-primary)); outline-offset: 3px; }
</style>
