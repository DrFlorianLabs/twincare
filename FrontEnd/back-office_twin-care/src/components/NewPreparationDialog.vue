<script setup>
import { computed, ref, watch } from 'vue'

import { localTimeZoneLabel, normalizePreparationDraft } from '../services/preparationStart'

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  loading: { type: Boolean, default: false },
  ambiguous: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'submit', 'draft-change', 'abandon'])

const patientReference = ref('')
const scheduledAt = ref('')
const appointmentReference = ref('')
const timeZone = localTimeZoneLabel()

const draft = computed(() => ({
  patientReference: patientReference.value,
  scheduledAt: scheduledAt.value,
  appointmentReference: appointmentReference.value
}))
const valid = computed(() => normalizePreparationDraft(draft.value) !== null)

function reset() {
  patientReference.value = ''
  scheduledAt.value = ''
  appointmentReference.value = ''
}

function draftChanged() {
  emit('draft-change')
}

function close() {
  if (!props.loading) emit('update:modelValue', false)
}

function submit() {
  if (props.ambiguous && !props.loading) {
    emit('submit')
    return
  }
  const normalized = normalizePreparationDraft(draft.value)
  if (normalized && !props.loading) emit('submit', normalized)
}

watch(() => props.modelValue, open => {
  if (!open) reset()
})
</script>

<template>
  <v-dialog
    :model-value="modelValue"
    max-width="620"
    persistent
    @update:model-value="value => !value && close()"
  >
    <v-card
      rounded="xl"
      aria-labelledby="new-preparation-title"
      aria-describedby="new-preparation-description"
    >
      <v-card-title id="new-preparation-title" class="px-5 pt-5">
        Nouvelle préparation
      </v-card-title>
      <v-card-text class="px-5">
        <p id="new-preparation-description" class="text-body-2 text-medium-emphasis mb-5">
          Saisissez uniquement la référence locale nécessaire pour retrouver la consultation.
          TwinCare ne crée pas de dossier médical.
        </p>
        <v-text-field
          v-model="patientReference"
          label="Référence patient locale"
          maxlength="128"
          autocomplete="off"
          required
          :disabled="loading || ambiguous"
          @update:model-value="draftChanged"
        />
        <v-text-field
          v-model="scheduledAt"
          :label="`Date et heure de la consultation (${timeZone})`"
          type="datetime-local"
          required
          :disabled="loading || ambiguous"
          @update:model-value="draftChanged"
        />
        <v-text-field
          v-model="appointmentReference"
          label="Référence du rendez-vous (facultatif)"
          maxlength="128"
          autocomplete="off"
          :disabled="loading || ambiguous"
          @update:model-value="draftChanged"
        />
        <v-alert v-if="error" type="error" variant="tonal" role="alert" class="mt-2">
          {{ error }}
        </v-alert>
        <v-alert v-if="loading" type="info" variant="tonal" role="status" class="mt-2">
          Création en cours… Ne fermez pas cette fenêtre.
        </v-alert>
      </v-card-text>
      <v-card-actions class="px-5 pb-5">
        <v-spacer />
        <v-btn
          v-if="ambiguous"
          variant="text"
          :disabled="loading"
          @click="emit('abandon')"
        >
          Abandonner et vérifier la liste
        </v-btn>
        <v-btn v-else variant="text" :disabled="loading" @click="close">Annuler</v-btn>
        <v-btn
          color="primary"
          variant="flat"
          :loading="loading"
          :disabled="(!ambiguous && !valid) || loading"
          @click="submit"
        >
          {{ ambiguous ? 'Réessayer la même demande' : 'Créer la préparation' }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
