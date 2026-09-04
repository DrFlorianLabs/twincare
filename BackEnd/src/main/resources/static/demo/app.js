const examples = [
  "Exemple fictif : une gêne au genou droit apparue après une promenade.",
  "Exemple fictif corrigé : une gêne au genou gauche apparue après une promenade, sans document disponible."
]

const labels = {
  PATIENT_DECLARED: "Déclaré par le patient",
  NOT_ASSESSED: "Non évaluée",
  UNREVIEWED: "À vérifier",
  CONFIRMED: "Confirmé",
  REJECTED: "Rejeté",
  COMPLETE: "Complet",
  INCOMPLETE: "Incomplet",
  NEEDS_CORRECTION: "À corriger",
  CONSULTATION_REASON: "Motif de consultation",
  PATIENT_ATTACHMENT: "Document patient",
  COMPLETED: "Renseigné",
  MISSING: "Manquant",
  AVAILABLE: "Disponible",
  NOT_PROVIDED: "Non fourni",
  OPTIONAL: "Facultatif",
  REQUIRED: "Requis"
}

const elements = {
  patientTab: document.querySelector("#patient-tab"),
  professionalTab: document.querySelector("#professional-tab"),
  patientPanel: document.querySelector("#patient-panel"),
  professionalPanel: document.querySelector("#professional-panel"),
  globalStatus: document.querySelector("#global-status"),
  reasonForm: document.querySelector("#reason-form"),
  reason: document.querySelector("#reason"),
  reasonCount: document.querySelector("#reason-count"),
  exampleButton: document.querySelector("#example-button"),
  submitReason: document.querySelector("#submit-reason"),
  patientProgress: document.querySelector("#patient-progress"),
  refreshButton: document.querySelector("#refresh-button"),
  currentReason: document.querySelector("#current-reason"),
  factMetadata: document.querySelector("#fact-metadata"),
  reviewChip: document.querySelector("#review-chip"),
  reviewActions: document.querySelector("#review-actions"),
  reviewGuidance: document.querySelector("#review-guidance"),
  confirmButton: document.querySelector("#confirm-button"),
  rejectButton: document.querySelector("#reject-button"),
  preparationItems: document.querySelector("#preparation-items"),
  readiness: document.querySelector("#readiness"),
  historyList: document.querySelector("#history-list"),
  historyCount: document.querySelector("#history-count"),
  emptyHistory: document.querySelector("#empty-history"),
  exportLink: document.querySelector("#export-link"),
  resetButton: document.querySelector("#reset-button")
}

let state = null
let selectedExample = 0
let busy = false

async function request(path, options = {}) {
  const response = await fetch(path, {
    cache: "no-store",
    headers: {
      Accept: "application/json",
      ...(options.body ? { "Content-Type": "application/json" } : {})
    },
    ...options
  })
  if (!response.ok) {
    throw new Error(`Demo request failed with status ${response.status}`)
  }
  if (response.status === 204) return null
  return response.json()
}

function setBusy(value) {
  busy = value
  for (const control of [
    elements.patientTab,
    elements.professionalTab,
    elements.exampleButton,
    elements.submitReason,
    elements.refreshButton,
    elements.confirmButton,
    elements.rejectButton,
    elements.resetButton,
    elements.reason
  ]) {
    control.disabled = value
  }
}

function announce(message, isError = false) {
  elements.globalStatus.textContent = message
  elements.globalStatus.classList.toggle("is-error", isError)
}

function showRole(role) {
  const patient = role === "patient"
  elements.patientPanel.hidden = !patient
  elements.professionalPanel.hidden = patient
  elements.patientTab.classList.toggle("is-active", patient)
  elements.professionalTab.classList.toggle("is-active", !patient)
  elements.patientTab.setAttribute("aria-pressed", String(patient))
  elements.professionalTab.setAttribute("aria-pressed", String(!patient))
  if (!patient) render()
}

function currentFact() {
  return state?.facts.find((fact) => fact.lifecycle.status === "CURRENT") ?? null
}

function formatDate(value) {
  if (!value) return "Non renseignée"
  return new Intl.DateTimeFormat("fr-FR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value))
}

function statusClass(reviewStatus) {
  if (reviewStatus === "CONFIRMED") return "status-positive"
  if (reviewStatus === "REJECTED") return "status-negative"
  if (reviewStatus === "UNREVIEWED") return "status-warning"
  return "status-neutral"
}

function renderMetadata(fact) {
  elements.factMetadata.replaceChildren()
  if (!fact) return
  const entries = [
    ["Provenance", labels[fact.provenance.type] ?? fact.provenance.type],
    ["Certitude", labels[fact.certainty] ?? fact.certainty],
    ["Recueilli le", formatDate(fact.temporal.recordedAt)]
  ]
  for (const [term, value] of entries) {
    const wrapper = document.createElement("div")
    const dt = document.createElement("dt")
    const dd = document.createElement("dd")
    dt.textContent = term
    dd.textContent = value
    wrapper.append(dt, dd)
    elements.factMetadata.append(wrapper)
  }
}

function renderPreparationItems() {
  elements.preparationItems.replaceChildren()
  for (const item of state?.preparationItems ?? []) {
    const li = document.createElement("li")
    const name = document.createElement("span")
    const status = document.createElement("span")
    name.textContent = `${labels[item.code] ?? item.code} · ${labels[item.requirement] ?? item.requirement}`
    status.className = "item-state"
    status.textContent = labels[item.status] ?? item.status
    li.append(name, status)
    elements.preparationItems.append(li)
  }
  elements.readiness.textContent = labels[state?.readiness] ?? "Incomplet"
}

function renderHistory() {
  const history = (state?.facts ?? [])
    .filter((fact) => fact.lifecycle.status === "SUPERSEDED")
    .slice()
    .reverse()
  elements.historyList.replaceChildren()
  for (const fact of history) {
    const li = document.createElement("li")
    const text = document.createElement("span")
    const time = document.createElement("time")
    text.textContent = fact.value.text
    time.dateTime = fact.lifecycle.supersededAt ?? fact.temporal.recordedAt
    time.textContent = `Remplacé le ${formatDate(time.dateTime)}`
    li.append(text, time)
    elements.historyList.append(li)
  }
  elements.historyCount.textContent = `${history.length} version${history.length > 1 ? "s" : ""} antérieure${history.length > 1 ? "s" : ""}`
  elements.emptyHistory.hidden = history.length > 0
}

function render() {
  if (!state) return
  const fact = currentFact()
  const hasReason = Boolean(fact)
  elements.patientProgress.textContent = hasReason ? "Motif transmis" : "À compléter"
  elements.patientProgress.className = `status-chip ${hasReason ? "status-positive" : "status-neutral"}`
  elements.submitReason.textContent = hasReason ? "Enregistrer la modification" : "Transmettre le motif"
  elements.currentReason.textContent = fact?.value.text ?? "Le patient n’a pas encore transmis de motif."
  renderMetadata(fact)

  const reviewStatus = fact?.review.status ?? null
  elements.reviewChip.textContent = labels[reviewStatus] ?? "Non renseigné"
  elements.reviewChip.className = `status-chip ${statusClass(reviewStatus)}`
  elements.reviewActions.hidden = reviewStatus !== "UNREVIEWED"
  if (!fact) {
    elements.reviewGuidance.textContent = "Revenez à la vue patient pour transmettre un motif fictif."
  } else if (reviewStatus === "UNREVIEWED") {
    elements.reviewGuidance.textContent = "La provenance décrit l’origine ; elle ne vaut pas validation clinique."
  } else if (reviewStatus === "CONFIRMED") {
    elements.reviewGuidance.textContent = "Le motif a été confirmé par l’équipe médicale."
  } else {
    elements.reviewGuidance.textContent = "Le motif doit être corrigé par le patient."
  }
  renderPreparationItems()
  renderHistory()
  elements.exportLink.setAttribute("aria-disabled", String(!state))
}

async function perform(action, successMessage) {
  if (busy) return
  setBusy(true)
  announce("Mise à jour en cours…")
  try {
    state = await action()
    render()
    announce(successMessage)
  } catch {
    announce("La démonstration n’a pas pu être mise à jour. Relancez-la ou réinitialisez-la.", true)
  } finally {
    setBusy(false)
  }
}

elements.patientTab.addEventListener("click", () => showRole("patient"))
elements.professionalTab.addEventListener("click", () => showRole("professional"))
elements.reason.addEventListener("input", () => {
  elements.reasonCount.textContent = `${elements.reason.value.length} / 4000`
})
elements.exampleButton.addEventListener("click", () => {
  elements.reason.value = examples[selectedExample]
  selectedExample = (selectedExample + 1) % examples.length
  elements.reason.dispatchEvent(new Event("input"))
  elements.reason.focus()
})
elements.reasonForm.addEventListener("submit", async (event) => {
  event.preventDefault()
  const reason = elements.reason.value.trim()
  if (!reason) {
    announce("Ajoutez un motif entièrement fictif avant de continuer.", true)
    elements.reason.focus()
    return
  }
  await perform(
    () => request("/demo/api/reason", { method: "PUT", body: JSON.stringify({ reason }) }),
    currentFact()?.value.text === reason
      ? "Le motif était déjà à jour. Aucun doublon n’a été créé."
      : "Motif fictif enregistré. Vous pouvez le modifier ou passer à la vue équipe médicale."
  )
})
elements.refreshButton.addEventListener("click", () => perform(
  () => request("/demo/api/state"),
  "Contexte actualisé."
))
elements.confirmButton.addEventListener("click", () => {
  const fact = currentFact()
  if (fact) perform(
    () => request(`/demo/api/facts/${fact.id}/review/CONFIRM`, { method: "PUT" }),
    "Motif confirmé."
  )
})
elements.rejectButton.addEventListener("click", () => {
  const fact = currentFact()
  if (fact) perform(
    () => request(`/demo/api/facts/${fact.id}/review/REJECT`, { method: "PUT" }),
    "Motif rejeté : la préparation signale maintenant une correction nécessaire."
  )
})
elements.resetButton.addEventListener("click", async () => {
  if (!window.confirm("Réinitialiser toutes les données fictives de la démonstration ?")) return
  await perform(
    () => request("/demo/api/reset", { method: "POST" }),
    "Démonstration réinitialisée."
  )
  elements.reason.value = ""
  elements.reason.dispatchEvent(new Event("input"))
  selectedExample = 0
  showRole("patient")
})

perform(
  () => request("/demo/api/state"),
  "Démonstration prête. Commencez par la vue patient."
)
