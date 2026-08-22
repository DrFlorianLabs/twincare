<script setup>
import { formatDateTime } from '../services/preparationPresentation'

defineProps({
  modelValue: { type: Boolean, required: true },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  linkReady: { type: Boolean, default: false },
  copied: { type: Boolean, default: false },
  expiresAt: { type: String, default: '' },
  configurationAvailable: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue', 'issue', 'copy'])
</script>

<template>
  <v-dialog
    :model-value="modelValue"
    max-width="620"
    persistent
    @update:model-value="value => !value && emit('update:modelValue', false)"
  >
    <v-card
      rounded="xl"
      aria-labelledby="patient-invitation-title"
      aria-describedby="patient-invitation-description"
    >
      <v-card-title id="patient-invitation-title" class="px-5 pt-5">
        Lien de préparation patient
      </v-card-title>
      <v-card-text class="px-5">
        <p id="patient-invitation-description" class="mb-4">
          Copiez le lien puis transmettez-le avec votre canal habituel. TwinCare n’envoie
          automatiquement ni SMS ni e-mail.
        </p>
        <v-alert v-if="!configurationAvailable" type="error" variant="tonal" role="alert">
          L’adresse de l’interface patient n’est pas configurée. Aucun lien ne peut être créé.
        </v-alert>
        <template v-else-if="linkReady">
          <v-alert type="success" variant="tonal" role="status" class="mb-4">
            Le lien temporaire est prêt. Il expire le {{ formatDateTime(expiresAt) }}.
          </v-alert>
          <p class="text-body-2 text-medium-emphasis mb-4">
            Le secret n’est pas affiché. TwinCare l’effacera de cet écran à la fermeture,
            mais une copie restera dans votre presse-papiers : traitez ce lien comme confidentiel.
          </p>
          <v-btn
            color="primary"
            variant="flat"
            prepend-icon="mdi-content-copy"
            :disabled="loading"
            @click="emit('copy')"
          >
            Copier le lien patient
          </v-btn>
          <p v-if="copied" class="text-body-2 text-success mt-3" role="status">
            Lien copié. Vous pouvez maintenant le transmettre au patient.
          </p>
        </template>
        <template v-else>
          <v-alert type="warning" variant="tonal" class="mb-4">
            Créer un nouveau lien invalide tout lien actif précédemment généré pour cette préparation.
          </v-alert>
          <v-btn
            color="primary"
            variant="flat"
            :loading="loading"
            :disabled="loading"
            @click="emit('issue')"
          >
            Créer le lien temporaire
          </v-btn>
        </template>
        <v-alert v-if="error" type="error" variant="tonal" role="alert" class="mt-4">
          {{ error }}
        </v-alert>
      </v-card-text>
      <v-card-actions class="px-5 pb-5">
        <v-spacer />
        <v-btn
          variant="text"
          :disabled="loading"
          @click="emit('update:modelValue', false)"
        >
          {{ linkReady ? 'Fermer et effacer le lien' : 'Fermer' }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
