<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'

import { clearProfessionalSession } from '../services/api'
import {
  closePreparationTask,
  confirmPreparationReady,
  createPreparationTask,
  downloadPreparationAttachment,
  getPreparation,
  getProfessionalContext,
  listPreparations
} from '../services/preparationService'
import {
  blockerLabel,
  formatBytes,
  formatDateTime,
  isPreparationCaseId,
  isPreparationTaskLabel,
  preparationCommandPolicy,
  preparationScopeKey,
  professionalErrorKind,
  readinessPresentation,
  scopedResultApplies,
  taskStatusLabel
} from '../services/preparationPresentation'

const route = useRoute()
const router = useRouter()

const context = ref(null)
const contextLoading = ref(true)
const contextError = ref('')
const selectedOrganizationId = ref('')

const page = ref(1)
const pageData = ref({ content: [], page: 0, totalElements: 0, totalPages: 0 })
const listLoading = ref(false)
const listError = ref('')

const preparation = ref(null)
const viewLoading = ref(false)
const viewError = ref('')
const feedback = ref(null)

const taskLabel = ref('')
const taskRequestId = ref(null)
const taskSubmitting = ref(false)
const taskActionId = ref('')
const confirming = ref(false)
const downloading = ref(false)
const detailHeading = ref(null)

let contextController
let listController
let viewController
let downloadController
let contextSequence = 0
let listSequence = 0
let viewSequence = 0
let pendingFocusCaseId = ''
let interactionEpoch = 0

const organizations = computed(() =>
  Array.isArray(context.value?.organizations)
    ? context.value.organizations.filter(organization =>
        Array.isArray(organization.capabilities)
        && organization.capabilities.includes('VIEW_PREPARATION'))
    : []
)

const selectedOrganization = computed(() =>
  organizations.value.find(organization =>
    organization.organizationId === selectedOrganizationId.value) || null
)

const sortedTasks = computed(() => {
  const tasks = Array.isArray(preparation.value?.tasks) ? [...preparation.value.tasks] : []
  return tasks.sort((left, right) => {
    if (left.status === 'OPEN' && right.status !== 'OPEN') return -1
    if (left.status !== 'OPEN' && right.status === 'OPEN') return 1
    return new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime()
  })
})

const currentReadiness = computed(() =>
  readinessPresentation(preparation.value?.readiness?.state)
)

const commandPolicy = computed(() => preparationCommandPolicy(preparation.value))

const canConfirm = computed(() =>
  hasCapability('CONFIRM_READY_FOR_CONSULTATION')
  && commandPolicy.value.confirmReady
)

const taskLabelValid = computed(() => isPreparationTaskLabel(taskLabel.value))

function hasCapability(capability) {
  return selectedOrganization.value?.capabilities?.includes(capability) === true
}

function isCancelled(error) {
  return error?.code === 'ERR_CANCELED' || error?.name === 'CanceledError'
}

function emptyPage() {
  return { content: [], page: 0, totalElements: 0, totalPages: 0 }
}

function currentScopeKey() {
  const routedCaseId = typeof route.params.caseId === 'string' ? route.params.caseId : ''
  return preparationScopeKey(selectedOrganizationId.value, routedCaseId)
}

function resultStillApplies(scope, epoch) {
  return scopedResultApplies(scope, currentScopeKey(), epoch, interactionEpoch)
}

function resetTaskDraft() {
  taskLabel.value = ''
  taskRequestId.value = null
}

function invalidateCaseScope() {
  interactionEpoch += 1
  viewSequence += 1
  viewController?.abort()
  downloadController?.abort()
  viewLoading.value = false
  viewError.value = ''
  taskSubmitting.value = false
  taskActionId.value = ''
  confirming.value = false
  downloading.value = false
  pendingFocusCaseId = ''
  resetTaskDraft()
  preparation.value = null
}

function invalidateOrganizationScope() {
  listSequence += 1
  listController?.abort()
  listLoading.value = false
  listError.value = ''
  pageData.value = emptyPage()
  invalidateCaseScope()
}

function setSelectedOrganization(organizationId) {
  if (organizationId === selectedOrganizationId.value) return
  invalidateOrganizationScope()
  selectedOrganizationId.value = organizationId
}

async function handleUnauthorized(error) {
  if (professionalErrorKind(error) !== 'UNAUTHENTICATED') return false
  invalidateOrganizationScope()
  clearProfessionalSession()
  await router.replace({ name: 'Login', query: { expired: '1' } })
  return true
}

async function handleForbidden(error) {
  if (professionalErrorKind(error) !== 'FORBIDDEN') return false
  setSelectedOrganization('')
  feedback.value = { type: 'warning', text: 'Vos droits ont changé. Le contexte a été rechargé.' }
  await loadContext()
  return true
}

function abortRequests() {
  contextController?.abort()
  listController?.abort()
  viewController?.abort()
  downloadController?.abort()
}

async function loadContext() {
  const sequence = ++contextSequence
  contextController?.abort()
  contextController = new AbortController()
  contextLoading.value = true
  contextError.value = ''
  try {
    const result = await getProfessionalContext({ signal: contextController.signal })
    if (sequence !== contextSequence) return
    context.value = result
    if (!Array.isArray(result?.organizations)) {
      throw new Error('Professional context is invalid')
    }
    const firstAccessible = result.organizations.find(organization =>
      organization?.capabilities?.includes('VIEW_PREPARATION'))
    setSelectedOrganization(firstAccessible?.organizationId || '')
    if (!firstAccessible) {
      contextError.value = 'Aucune organisation clinique active n’est associée à votre compte.'
    }
  } catch (error) {
    if (isCancelled(error) || sequence !== contextSequence) return
    if (await handleUnauthorized(error)) return
    context.value = null
    setSelectedOrganization('')
    contextError.value = professionalErrorKind(error) === 'FORBIDDEN'
      ? 'Votre compte ne dispose pas d’un accès professionnel à TwinCare Consultation.'
      : 'Le contexte professionnel est momentanément indisponible.'
  } finally {
    if (sequence === contextSequence) contextLoading.value = false
  }
}

async function loadList() {
  if (!selectedOrganizationId.value) return
  const sequence = ++listSequence
  listController?.abort()
  listController = new AbortController()
  listLoading.value = true
  listError.value = ''
  try {
    const result = await listPreparations(
      selectedOrganizationId.value,
      page.value - 1,
      20,
      { signal: listController.signal }
    )
    if (sequence !== listSequence) return
    if (!Array.isArray(result?.content)) throw new Error('Preparation page is invalid')
    pageData.value = result
  } catch (error) {
    if (isCancelled(error) || sequence !== listSequence) return
    if (await handleUnauthorized(error)) return
    if (await handleForbidden(error)) return
    listError.value = 'Les préparations ne peuvent pas être chargées pour le moment.'
  } finally {
    if (sequence === listSequence) listLoading.value = false
  }
}

async function loadPreparation(caseId) {
  if (!selectedOrganizationId.value || !caseId) return
  if (!isPreparationCaseId(caseId)) {
    await router.replace({ name: 'Preparations' })
    return
  }
  const sequence = ++viewSequence
  viewController?.abort()
  viewController = new AbortController()
  viewLoading.value = true
  viewError.value = ''
  preparation.value = null
  try {
    const result = await getPreparation(
      selectedOrganizationId.value,
      caseId,
      { signal: viewController.signal }
    )
    if (sequence !== viewSequence) return
    preparation.value = result
    if (pendingFocusCaseId === caseId) {
      pendingFocusCaseId = ''
      await nextTick()
      detailHeading.value?.focus()
    }
  } catch (error) {
    if (isCancelled(error) || sequence !== viewSequence) return
    if (await handleUnauthorized(error)) return
    if (await handleForbidden(error)) return
    if (professionalErrorKind(error) === 'NOT_FOUND') {
      feedback.value = { type: 'warning', text: 'Cette préparation n’est plus disponible.' }
      await router.replace({ name: 'Preparations' })
      await loadList()
      return
    }
    viewError.value = 'Le détail de la préparation est momentanément indisponible.'
  } finally {
    if (sequence === viewSequence) viewLoading.value = false
  }
}

function prepareSelection() {
  feedback.value = null
}

async function refreshCurrent(scope, epoch) {
  if (!resultStillApplies(scope, epoch)) return
  const caseId = route.params.caseId
  await Promise.all([loadList(), caseId ? loadPreparation(caseId) : Promise.resolve()])
}

async function submitTask() {
  const label = taskLabel.value.trim()
  if (!isPreparationTaskLabel(label)
      || !preparation.value
      || !commandPolicy.value.createTask
      || taskSubmitting.value) return
  const scope = currentScopeKey()
  const epoch = interactionEpoch
  const organizationId = selectedOrganizationId.value
  const caseId = preparation.value.caseId
  if (!taskRequestId.value) taskRequestId.value = crypto.randomUUID()
  const requestId = taskRequestId.value
  taskSubmitting.value = true
  feedback.value = null
  try {
    await createPreparationTask(organizationId, caseId, {
      requestId,
      label
    })
    if (!resultStillApplies(scope, epoch)) return
    resetTaskDraft()
    feedback.value = { type: 'success', text: 'La tâche de préparation a été ajoutée.' }
    await refreshCurrent(scope, epoch)
  } catch (error) {
    if (await handleUnauthorized(error)) return
    if (!resultStillApplies(scope, epoch)) return
    if (await handleForbidden(error)) return
    if (professionalErrorKind(error) === 'CONFLICT') {
      feedback.value = { type: 'warning', text: 'La préparation a changé. La vue a été actualisée.' }
      await refreshCurrent(scope, epoch)
    } else {
      feedback.value = { type: 'error', text: 'La tâche n’a pas pu être enregistrée. Réessayez.' }
    }
  } finally {
    if (resultStillApplies(scope, epoch)) taskSubmitting.value = false
  }
}

async function changeTaskStatus(task, status) {
  if (task.status !== 'OPEN' || !commandPolicy.value.changeTaskStatus || taskActionId.value) return
  const scope = currentScopeKey()
  const epoch = interactionEpoch
  const organizationId = selectedOrganizationId.value
  const caseId = preparation.value.caseId
  taskActionId.value = task.id
  feedback.value = null
  try {
    await closePreparationTask(
      organizationId,
      caseId,
      task.id,
      status
    )
    if (!resultStillApplies(scope, epoch)) return
    feedback.value = {
      type: 'success',
      text: status === 'COMPLETED' ? 'La tâche est terminée.' : 'La tâche est annulée.'
    }
    await refreshCurrent(scope, epoch)
  } catch (error) {
    if (await handleUnauthorized(error)) return
    if (!resultStillApplies(scope, epoch)) return
    if (await handleForbidden(error)) return
    feedback.value = professionalErrorKind(error) === 'CONFLICT'
      ? { type: 'warning', text: 'La préparation a changé. La vue a été actualisée.' }
      : { type: 'error', text: 'La tâche n’a pas pu être mise à jour.' }
    await refreshCurrent(scope, epoch)
  } finally {
    if (resultStillApplies(scope, epoch)) taskActionId.value = ''
  }
}

async function confirmReady() {
  if (!canConfirm.value || confirming.value) return
  const scope = currentScopeKey()
  const epoch = interactionEpoch
  const organizationId = selectedOrganizationId.value
  const caseId = preparation.value.caseId
  const revision = preparation.value.revision
  confirming.value = true
  feedback.value = null
  try {
    await confirmPreparationReady(
      organizationId,
      caseId,
      revision
    )
    if (!resultStillApplies(scope, epoch)) return
    feedback.value = { type: 'success', text: 'La préparation est confirmée prête.' }
    await refreshCurrent(scope, epoch)
  } catch (error) {
    if (await handleUnauthorized(error)) return
    if (!resultStillApplies(scope, epoch)) return
    if (await handleForbidden(error)) return
    feedback.value = professionalErrorKind(error) === 'CONFLICT'
      ? { type: 'warning', text: 'La préparation a changé depuis votre lecture. Relisez-la avant de confirmer.' }
      : { type: 'error', text: 'La confirmation n’a pas pu être enregistrée.' }
    await refreshCurrent(scope, epoch)
  } finally {
    if (resultStillApplies(scope, epoch)) confirming.value = false
  }
}

async function downloadAttachment() {
  const attachment = preparation.value?.attachment
  if (!attachment?.present || downloading.value) return
  const scope = currentScopeKey()
  const epoch = interactionEpoch
  const organizationId = selectedOrganizationId.value
  const caseId = preparation.value.caseId
  downloadController?.abort()
  downloadController = new AbortController()
  downloading.value = true
  feedback.value = null
  try {
    await downloadPreparationAttachment(
      organizationId,
      caseId,
      attachment.displayFilename,
      attachment.sizeBytes,
      { signal: downloadController.signal }
    )
  } catch (error) {
    if (isCancelled(error)) return
    if (await handleUnauthorized(error)) return
    if (!resultStillApplies(scope, epoch)) return
    if (await handleForbidden(error)) return
    if (professionalErrorKind(error) === 'NOT_FOUND') {
      feedback.value = { type: 'warning', text: 'Le document n’est plus associé à cette préparation.' }
      await refreshCurrent(scope, epoch)
    } else if (professionalErrorKind(error) === 'UNAVAILABLE') {
      feedback.value = { type: 'error', text: 'Le document est temporairement indisponible. Réessayez plus tard.' }
    } else {
      feedback.value = { type: 'error', text: 'Le document n’a pas pu être vérifié et téléchargé.' }
    }
  } finally {
    if (resultStillApplies(scope, epoch)) downloading.value = false
  }
}

async function logout() {
  abortRequests()
  invalidateOrganizationScope()
  clearProfessionalSession()
  context.value = null
  await router.replace({ name: 'Login' })
}

watch(selectedOrganizationId, async (organizationId, previousOrganizationId) => {
  if (!organizationId) return
  const alreadyFirstPage = page.value === 1
  page.value = 1
  if (previousOrganizationId) {
    feedback.value = null
    await router.replace({ name: 'Preparations' })
  }
  if (alreadyFirstPage) await loadList()
  if (!previousOrganizationId && route.params.caseId) {
    await loadPreparation(route.params.caseId)
  }
})

watch(() => route.params.caseId, async (caseId, previousCaseId) => {
  if (!selectedOrganizationId.value || caseId === previousCaseId) return
  if (!caseId) {
    return
  }
  await loadPreparation(caseId)
})

onBeforeRouteUpdate((to, from) => {
  if (to.params.caseId !== from.params.caseId) {
    invalidateCaseScope()
    pendingFocusCaseId = typeof to.params.caseId === 'string' ? to.params.caseId : ''
  }
})

watch(page, loadList)
watch(taskLabel, () => {
  if (!taskSubmitting.value) taskRequestId.value = null
})

onMounted(loadContext)
onBeforeUnmount(abortRequests)
</script>

<template>
  <v-app-bar color="surface" elevation="1" height="72">
    <v-app-bar-title>
      <div class="d-flex align-center ga-3">
        <span class="brand-mark" aria-hidden="true">TC</span>
        <div>
          <div class="text-subtitle-1 font-weight-bold">TwinCare Consultation</div>
          <div class="text-caption text-medium-emphasis">Préparation en amont</div>
        </div>
      </div>
    </v-app-bar-title>
    <template #append>
      <div class="d-none d-sm-flex align-center ga-3 mr-2">
        <span class="text-body-2">{{ context?.displayName || 'Professionnel' }}</span>
        <v-btn icon="mdi-logout" variant="text" aria-label="Se déconnecter" @click="logout" />
      </div>
      <v-btn
        class="d-sm-none"
        icon="mdi-logout"
        variant="text"
        aria-label="Se déconnecter"
        @click="logout"
      />
    </template>
  </v-app-bar>

  <v-main class="workspace-background">
    <v-container fluid class="workspace-container pa-4 pa-md-6">
      <v-alert type="warning" variant="tonal" density="compact" class="mb-4">
        Développement sur données synthétiques uniquement — aucun patient réel à ce stade.
      </v-alert>

      <v-skeleton-loader v-if="contextLoading" type="heading, paragraph, card" />

      <v-alert v-else-if="contextError" type="error" variant="tonal" role="alert">
        {{ contextError }}
        <template #append>
          <v-btn variant="text" @click="loadContext">Réessayer</v-btn>
          <v-btn variant="text" @click="logout">Se déconnecter</v-btn>
        </template>
      </v-alert>

      <template v-else>
        <div class="workspace-heading mb-5">
          <div>
            <p class="text-overline text-primary mb-1">File de préparation</p>
            <h1 class="text-h4 font-weight-bold">Consultations à préparer</h1>
          </div>
          <v-select
            :model-value="selectedOrganizationId"
            :items="organizations"
            item-title="displayName"
            item-value="organizationId"
            label="Organisation"
            variant="outlined"
            hide-details
            class="organization-select"
            @update:model-value="setSelectedOrganization"
          />
        </div>

        <v-alert
          v-if="feedback"
          :type="feedback.type"
          variant="tonal"
          closable
          class="mb-4"
          role="status"
          @click:close="feedback = null"
        >
          {{ feedback.text }}
        </v-alert>

        <v-row align="start">
          <v-col cols="12" lg="4">
            <v-card rounded="xl" elevation="1" class="list-card">
              <v-card-title class="d-flex align-center justify-space-between px-5 pt-5">
                <span>Préparations</span>
                <v-chip size="small" variant="tonal">{{ pageData.totalElements }}</v-chip>
              </v-card-title>

              <v-alert v-if="listError" type="error" variant="text" class="mx-3" role="alert">
                {{ listError }}
                <v-btn variant="text" size="small" @click="loadList">Réessayer</v-btn>
              </v-alert>

              <v-skeleton-loader v-if="listLoading" type="list-item-three-line@5" />

              <div v-else-if="pageData.content.length === 0" class="empty-state pa-8 text-center">
                <v-icon icon="mdi-calendar-check-outline" size="44" color="primary" />
                <p class="text-subtitle-1 font-weight-medium mt-3">Aucune préparation</p>
                <p class="text-body-2 text-medium-emphasis">
                  Les préparations de cette organisation apparaîtront ici.
                </p>
              </div>

              <v-list v-else lines="three" class="px-2 pb-2" aria-label="Préparations disponibles">
                <v-list-item
                  v-for="item in pageData.content"
                  :key="item.caseId"
                  :to="{ name: 'Preparations', params: { caseId: item.caseId } }"
                  tabindex="0"
                  :active="route.params.caseId === item.caseId"
                  rounded="lg"
                  class="preparation-list-item mb-1"
                  @click="prepareSelection"
                >
                  <template #prepend>
                    <v-icon
                      :icon="readinessPresentation(item.readiness?.state).icon"
                      :color="readinessPresentation(item.readiness?.state).color"
                    />
                  </template>
                  <v-list-item-title class="font-weight-bold">
                    {{ item.patientReference }}
                  </v-list-item-title>
                  <v-list-item-subtitle>
                    Référence patient non vérifiée · {{ formatDateTime(item.scheduledAt) }}
                  </v-list-item-subtitle>
                  <template #append>
                    <div class="text-right">
                      <v-chip
                        size="x-small"
                        :color="readinessPresentation(item.readiness?.state).color"
                        variant="tonal"
                      >
                        {{ readinessPresentation(item.readiness?.state).label }}
                      </v-chip>
                      <div class="text-caption mt-1">
                        {{ item.openTaskCount }} action{{ item.openTaskCount > 1 ? 's' : '' }}
                      </div>
                    </div>
                  </template>
                </v-list-item>
              </v-list>

              <v-pagination
                v-if="pageData.totalPages > 1"
                v-model="page"
                :length="pageData.totalPages"
                :total-visible="5"
                density="comfortable"
                class="pb-3"
                aria-label="Pages de préparations"
              />
            </v-card>
          </v-col>

          <v-col cols="12" lg="8">
            <v-skeleton-loader v-if="viewLoading" type="heading, paragraph, card, card" />

            <v-alert v-else-if="viewError" type="error" variant="tonal" role="alert">
              {{ viewError }}
              <v-btn variant="text" @click="loadPreparation(route.params.caseId)">Réessayer</v-btn>
            </v-alert>

            <v-card v-else-if="!preparation" rounded="xl" elevation="1" class="empty-detail">
              <v-icon icon="mdi-file-document-check-outline" size="56" color="primary" />
              <h2 class="text-h6 mt-4">Sélectionnez une préparation</h2>
              <p class="text-body-2 text-medium-emphasis mt-2">
                Vous verrez ici le motif, les actions et le document éventuellement transmis.
              </p>
            </v-card>

            <template v-else>
              <v-card rounded="xl" elevation="1" class="mb-4">
                <v-card-text class="pa-5 pa-md-6">
                  <div class="detail-heading">
                    <div>
                      <p class="text-overline text-primary mb-1">Référence patient non vérifiée</p>
                      <h2 ref="detailHeading" class="text-h4 font-weight-bold" tabindex="-1">
                        {{ preparation.patientReference }}
                      </h2>
                      <p class="text-body-2 text-medium-emphasis mt-1">
                        Consultation : {{ formatDateTime(preparation.scheduledAt) }}
                      </p>
                    </div>
                    <v-chip
                      :color="currentReadiness.color"
                      :prepend-icon="currentReadiness.icon"
                      variant="tonal"
                      size="large"
                    >
                      {{ currentReadiness.label }}
                    </v-chip>
                  </div>

                  <v-alert
                    v-if="!commandPolicy.trusted"
                    type="error"
                    variant="tonal"
                    class="mt-5"
                  >
                    La cohérence de cet état ne peut pas être vérifiée. Les actions sont désactivées par sécurité.
                  </v-alert>

                  <div v-else-if="preparation.readiness?.blockers?.length" class="mt-5">
                    <p class="text-subtitle-2 mb-2">À traiter avant confirmation</p>
                    <v-chip
                      v-for="blocker in preparation.readiness.blockers"
                      :key="blocker"
                      color="warning"
                      variant="tonal"
                      class="mr-2 mb-2"
                    >
                      {{ blockerLabel(blocker) }}
                    </v-chip>
                  </div>

                  <v-btn
                    v-if="hasCapability('CONFIRM_READY_FOR_CONSULTATION')"
                    color="primary"
                    prepend-icon="mdi-check-decagram-outline"
                    :disabled="!canConfirm"
                    :loading="confirming"
                    class="mt-5"
                    @click="confirmReady"
                  >
                    Confirmer prête pour la consultation
                  </v-btn>
                </v-card-text>
              </v-card>

              <v-row>
                <v-col cols="12" md="7">
                  <v-card rounded="xl" elevation="1" class="h-100">
                    <v-card-title class="px-5 pt-5">Demande du patient</v-card-title>
                    <v-card-text class="px-5 pb-5">
                      <template v-if="preparation.patientReason?.present">
                        <v-chip size="small" color="info" variant="tonal" class="mb-4">
                          Déclaré par le patient · non vérifié
                        </v-chip>
                        <p class="patient-reason">{{ preparation.patientReason.text }}</p>
                        <p class="text-caption text-medium-emphasis mt-4">
                          Reçu le {{ formatDateTime(preparation.patientReason.recordedAt) }}
                        </p>
                      </template>
                      <v-alert v-else type="warning" variant="tonal">
                        Le patient n’a pas encore renseigné le motif de consultation.
                      </v-alert>
                    </v-card-text>
                  </v-card>
                </v-col>

                <v-col cols="12" md="5">
                  <v-card rounded="xl" elevation="1" class="h-100">
                    <v-card-title class="px-5 pt-5">Document</v-card-title>
                    <v-card-text class="px-5 pb-5">
                      <template v-if="preparation.attachment?.present">
                        <v-icon icon="mdi-file-lock-outline" size="36" color="primary" />
                        <p class="font-weight-medium mt-3 attachment-name">
                          {{ preparation.attachment.displayFilename }}
                        </p>
                        <p class="text-caption text-medium-emphasis">
                          {{ preparation.attachment.mediaType }} ·
                          {{ formatBytes(preparation.attachment.sizeBytes) }}
                        </p>
                        <v-btn
                          v-if="hasCapability('DOWNLOAD_ATTACHMENT')"
                          variant="outlined"
                          color="primary"
                          prepend-icon="mdi-download"
                          :loading="downloading"
                          class="mt-4"
                          @click="downloadAttachment"
                        >
                          Télécharger
                        </v-btn>
                      </template>
                      <div v-else class="text-medium-emphasis">
                        <v-icon icon="mdi-file-outline" size="36" />
                        <p class="text-body-2 mt-3">
                          Aucun document transmis. Ce n’est pas, à lui seul, une information manquante.
                        </p>
                      </div>
                    </v-card-text>
                  </v-card>
                </v-col>
              </v-row>

              <v-card rounded="xl" elevation="1" class="mt-4">
                <v-card-title class="d-flex align-center justify-space-between px-5 pt-5">
                  <span>Actions préparatoires</span>
                  <v-chip size="small" color="primary" variant="tonal">
                    {{ preparation.openTaskCount }} ouverte{{ preparation.openTaskCount > 1 ? 's' : '' }}
                  </v-chip>
                </v-card-title>
                <v-card-text class="px-5 pb-5">
                  <v-form
                    v-if="hasCapability('MANAGE_PREPARATION_TASKS') && commandPolicy.createTask"
                    class="task-form mb-5"
                    @submit.prevent="submitTask"
                  >
                    <v-text-field
                      v-model="taskLabel"
                      label="Nouvelle action à préparer"
                      placeholder="Ex. vérifier la date du dernier examen"
                      :rules="[
                        value => !value || isPreparationTaskLabel(value)
                          || '240 caractères maximum'
                      ]"
                      hide-details="auto"
                      :disabled="taskSubmitting"
                    />
                    <v-btn
                      type="submit"
                      color="primary"
                      prepend-icon="mdi-plus"
                      :disabled="!taskLabelValid"
                      :loading="taskSubmitting"
                    >
                      Ajouter
                    </v-btn>
                  </v-form>

                  <p v-if="sortedTasks.length === 0" class="text-body-2 text-medium-emphasis">
                    Aucune action préparatoire enregistrée.
                  </p>

                  <v-list v-else class="pa-0" aria-label="Actions préparatoires">
                    <v-list-item
                      v-for="task in sortedTasks"
                      :key="task.id"
                      class="task-item px-0"
                    >
                      <template #prepend>
                        <v-icon
                          :icon="task.status === 'OPEN' ? 'mdi-checkbox-blank-circle-outline' : 'mdi-check-circle-outline'"
                          :color="task.status === 'OPEN' ? 'warning' : 'success'"
                        />
                      </template>
                      <v-list-item-title :class="{ 'text-decoration-line-through': task.status !== 'OPEN' }">
                        {{ task.label }}
                      </v-list-item-title>
                      <v-list-item-subtitle>
                        {{ taskStatusLabel(task.status) }} ·
                        {{ task.assigneeUserId === context.actorUserId ? 'Vous' : 'Autre membre' }}
                      </v-list-item-subtitle>
                      <template
                        v-if="task.status === 'OPEN'
                          && hasCapability('MANAGE_PREPARATION_TASKS')
                          && commandPolicy.changeTaskStatus"
                        #append
                      >
                        <div class="d-flex ga-1 flex-wrap justify-end">
                          <v-btn
                            size="small"
                            variant="tonal"
                            color="success"
                            :loading="taskActionId === task.id"
                            @click="changeTaskStatus(task, 'COMPLETED')"
                          >
                            Terminer
                          </v-btn>
                          <v-btn
                            size="small"
                            variant="text"
                            :disabled="Boolean(taskActionId)"
                            @click="changeTaskStatus(task, 'CANCELLED')"
                          >
                            Annuler
                          </v-btn>
                        </div>
                      </template>
                    </v-list-item>
                  </v-list>
                </v-card-text>
              </v-card>
            </template>
          </v-col>
        </v-row>
      </template>
    </v-container>
  </v-main>
</template>

<style scoped>
.workspace-background {
  background: #f4f7f5;
}

.workspace-container {
  max-width: 1540px;
}

.brand-mark {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  place-items: center;
  border-radius: 0.8rem;
  color: white;
  background: #165c4a;
  font-weight: 800;
}

.workspace-heading,
.detail-heading,
.task-form {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.organization-select {
  max-width: 24rem;
}

.list-card {
  max-height: calc(100vh - 12.5rem);
  overflow-y: auto;
}

.preparation-list-item {
  cursor: pointer;
}

.empty-detail {
  display: grid;
  min-height: 28rem;
  place-content: center;
  justify-items: center;
  padding: 3rem;
  text-align: center;
}

.patient-reason {
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.attachment-name {
  overflow-wrap: anywhere;
}

.task-form .v-text-field {
  flex: 1;
}

.task-item + .task-item {
  border-top: 1px solid rgba(0, 0, 0, 0.08);
}

@media (max-width: 959px) {
  .workspace-heading,
  .detail-heading,
  .task-form {
    align-items: stretch;
    flex-direction: column;
  }

  .organization-select {
    width: 100%;
    max-width: none;
  }

  .list-card {
    max-height: none;
  }
}
</style>
