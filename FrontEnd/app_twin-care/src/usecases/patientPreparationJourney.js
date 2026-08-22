import { reactive } from 'vue'

import { InvitationCaptureKind } from '../security/invitationSecret.js'
import { PatientAccessError, PatientAccessErrorKind } from '../services/patientAccessService.js'

/**
 * @typedef {import('../security/invitationSecret.js').InvitationCapture} InvitationCapture
 * @typedef {import('../services/patientAccessService.js').PatientAccessClient} PatientAccessClient
 * @typedef {'LOADING' | 'FORM' | 'SAVED' | 'LINK_ERROR' | 'NETWORK_ERROR' | 'SESSION_EXPIRED'} PatientJourneyPhaseValue
 * @typedef {{
 *   phase: PatientJourneyPhaseValue,
 *   draft: string,
 *   savedReason: string,
 *   formError: string,
 *   saving: boolean,
 *   expiresAt: string | null
 * }} PatientJourneyState
 */

export const PatientJourneyPhase = Object.freeze({
  LOADING: 'LOADING',
  FORM: 'FORM',
  SAVED: 'SAVED',
  LINK_ERROR: 'LINK_ERROR',
  NETWORK_ERROR: 'NETWORK_ERROR',
  SESSION_EXPIRED: 'SESSION_EXPIRED',
})

const MAX_REASON_LENGTH = 4_000

/** @param {PatientAccessClient} client */
export function createPatientPreparationJourney(client) {
  const state = reactive(/** @type {PatientJourneyState} */ ({
    phase: PatientJourneyPhase.LOADING,
    draft: '',
    savedReason: '',
    formError: '',
    saving: false,
    expiresAt: null,
  }))

  /** @type {string | null} */
  let invitationToken = null
  let exchangeMayHaveSucceeded = false
  let startInProgress = false

  /**
   * @param {unknown} error
   * @param {boolean} invitationWasProvided
   */
  function showSessionError(error, invitationWasProvided) {
    client.clearSecrets()
    if (
      !(error instanceof PatientAccessError) ||
      error.kind === PatientAccessErrorKind.NETWORK ||
      error.kind === PatientAccessErrorKind.UNEXPECTED_RESPONSE
    ) {
      state.phase = PatientJourneyPhase.NETWORK_ERROR
      return
    }
    invitationToken = null
    exchangeMayHaveSucceeded = false
    state.phase = invitationWasProvided
      ? PatientJourneyPhase.LINK_ERROR
      : PatientJourneyPhase.SESSION_EXPIRED
  }

  async function loadSession() {
    const session = await client.restoreSession()
    const reason = await client.readReason()
    state.expiresAt = session.expiresAt
    if (reason.present) {
      state.savedReason = reason.reason
      state.draft = reason.reason
      state.phase = PatientJourneyPhase.SAVED
    } else {
      state.savedReason = ''
      state.draft = ''
      state.phase = PatientJourneyPhase.FORM
    }
  }

  /** @param {string} token */
  async function exchangeThenLoad(token) {
    exchangeMayHaveSucceeded = true
    await client.exchangeInvitation(token)
    invitationToken = null
    exchangeMayHaveSucceeded = false
    await loadSession()
  }

  /** @param {InvitationCapture} capture */
  async function start(capture) {
    if (startInProgress) return
    startInProgress = true
    state.phase = PatientJourneyPhase.LOADING
    state.formError = ''

    try {
      if (capture.kind === InvitationCaptureKind.INVALID) {
        state.phase = PatientJourneyPhase.LINK_ERROR
        return
      }
      invitationToken = capture.kind === InvitationCaptureKind.TOKEN ? capture.token : null
      if (capture.kind === InvitationCaptureKind.TOKEN) {
        capture.token = null
      }
      if (invitationToken) {
        await exchangeThenLoad(invitationToken)
      } else {
        await loadSession()
      }
    } catch (error) {
      showSessionError(error, capture.kind === InvitationCaptureKind.TOKEN)
    } finally {
      startInProgress = false
    }
  }

  async function retryConnection() {
    if (startInProgress) return
    startInProgress = true
    state.phase = PatientJourneyPhase.LOADING
    try {
      if (invitationToken && exchangeMayHaveSucceeded) {
        try {
          await loadSession()
          invitationToken = null
          exchangeMayHaveSucceeded = false
          return
        } catch (error) {
          if (!(error instanceof PatientAccessError) || error.kind !== PatientAccessErrorKind.UNAUTHORIZED) {
            throw error
          }
        }
      }
      if (invitationToken) {
        await exchangeThenLoad(invitationToken)
      } else {
        await loadSession()
      }
    } catch (error) {
      showSessionError(error, Boolean(invitationToken))
    } finally {
      startInProgress = false
    }
  }

  function editReason() {
    state.draft = state.savedReason
    state.formError = ''
    state.phase = PatientJourneyPhase.FORM
  }

  function cancelEdit() {
    if (!state.savedReason) return
    state.draft = state.savedReason
    state.formError = ''
    state.phase = PatientJourneyPhase.SAVED
  }

  function canSave() {
    const normalized = state.draft.trim()
    return (
      !state.saving &&
      normalized.length > 0 &&
      normalized.length <= MAX_REASON_LENGTH &&
      !normalized.includes('\0')
    )
  }

  async function saveReason() {
    if (state.saving) return
    if (!canSave()) {
      state.formError = `Votre réponse doit contenir entre 1 et ${MAX_REASON_LENGTH.toLocaleString('fr-FR')} caractères.`
      return
    }
    const normalized = state.draft.trim()
    state.saving = true
    state.formError = ''
    try {
      await client.saveReason(normalized)
      state.savedReason = normalized
      state.draft = normalized
      state.phase = PatientJourneyPhase.SAVED
    } catch (error) {
      if (error instanceof PatientAccessError && error.kind === PatientAccessErrorKind.UNAUTHORIZED) {
        client.clearSecrets()
        state.phase = PatientJourneyPhase.SESSION_EXPIRED
      } else {
        state.formError =
          'L’enregistrement n’a pas abouti. Votre texte reste affiché ; vous pouvez réessayer.'
      }
    } finally {
      state.saving = false
    }
  }

  return Object.freeze({
    state,
    start,
    retryConnection,
    editReason,
    cancelEdit,
    canSave,
    saveReason,
  })
}
