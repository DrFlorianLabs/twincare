<script setup>
import { ref } from 'vue'

defineProps({
  canIssueInvitation: { type: Boolean, required: true },
  canConfirmReady: { type: Boolean, required: true },
  canClose: { type: Boolean, required: true },
  confirmingReady: { type: Boolean, default: false },
  closing: { type: Boolean, default: false }
})

const emit = defineEmits(['issue-invitation', 'confirm-ready', 'close'])
const closeButton = ref(null)

function focusClose() {
  const element = closeButton.value?.$el || closeButton.value
  element?.focus()
}

defineExpose({ focusClose })
</script>

<template>
  <div v-if="canIssueInvitation || canConfirmReady || canClose" class="mt-5">
    <v-btn
      v-if="canIssueInvitation"
      color="primary"
      prepend-icon="mdi-link-variant-plus"
      @click="emit('issue-invitation')"
    >
      Créer le lien patient
    </v-btn>
    <v-btn
      v-else-if="canConfirmReady"
      color="primary"
      prepend-icon="mdi-check-decagram-outline"
      :loading="confirmingReady"
      @click="emit('confirm-ready')"
    >
      Confirmer prête pour la consultation
    </v-btn>
    <v-btn
      v-else-if="canClose"
      ref="closeButton"
      color="primary"
      prepend-icon="mdi-archive-check-outline"
      :loading="closing"
      @click="emit('close')"
    >
      Clôturer après la consultation
    </v-btn>
  </div>
</template>
