export const CASE_ID = '2f3de63b-692d-4c20-8a48-c7ec01b95b21'
export const FACT_ID = '3f3de63b-692d-4c20-8a48-c7ec01b95b22'
export const TIME = '2099-01-01T10:00:00Z'
export function clinicalFixture() {
  const preparation = {
    caseId: CASE_ID, patientReference: 'synthetic-patient-42',
    appointmentReference: 'synthetic-appointment-42', scheduledAt: '2099-01-02T10:00:00Z',
    caseStatus: 'READY_FOR_REVIEW', revision: 4,
    readiness: { state: 'REVIEWABLE', blockers: [], ruleSetId: 'CORE_PREPARATION',
      ruleSetVersion: 1, provenance: 'DERIVED_DETERMINISTIC' },
    patientReason: { present: true, text: 'Motif synthétique actuel',
      provenance: 'PATIENT_DECLARED', recordedAt: TIME },
    attachment: { present: true, displayFilename: 'document-synthetique.pdf',
      mediaType: 'application/pdf', sizeBytes: 123, uploadedAt: TIME, updatedAt: TIME },
    tasks: [{ id: '4f3de63b-692d-4c20-8a48-c7ec01b95b23', label: 'Tâche synthétique',
      status: 'COMPLETED', createdAt: TIME, updatedAt: TIME, closedAt: TIME }],
    readyConfirmation: { confirmed: false }, openTaskCount: 0,
    createdAt: TIME, updatedAt: TIME
  }
  const context = {
    schemaVersion: 'twincare.clinical-context.v1', contextId: CASE_ID,
    preparationRevision: 4, preparationStatus: 'READY_FOR_REVIEW',
    scheduledAt: preparation.scheduledAt, readiness: 'COMPLETE', generatedAt: TIME,
    preparationItems: [
      { code: 'CONSULTATION_REASON', requirement: 'REQUIRED', status: 'COMPLETED' },
      { code: 'PATIENT_ATTACHMENT', requirement: 'OPTIONAL', status: 'AVAILABLE' }
    ],
    facts: [{ id: FACT_ID, kind: 'CONSULTATION_REASON',
      value: { text: preparation.patientReason.text },
      provenance: { type: 'PATIENT_DECLARED' }, certainty: 'NOT_ASSESSED',
      temporal: { recordedAt: TIME, effectiveFrom: null, effectiveTo: null },
      lifecycle: { status: 'CURRENT', supersededAt: null },
      review: { status: 'UNREVIEWED', reviewedAt: null } }],
    documents: [{ id: '5f3de63b-692d-4c20-8a48-c7ec01b95b24',
      displayFilename: preparation.attachment.displayFilename,
      mediaType: 'PDF', sizeBytes: 123, provenance: 'PATIENT_UPLOADED',
      uploadedAt: TIME, updatedAt: TIME }]
  }
  return { context, preparation }
}
