# TwinCare — Product Scope V0.1

Status: **normative for current implementation**

## 1. Problem to solve

TwinCare reduces the time a physician loses because a consultation starts with incomplete, scattered or poorly structured information.

The target failure modes are concrete:

- discovering the real reason for consultation only when the patient enters;
- missing documents or results;
- reconstructing history across multiple sources;
- performing during the medical consultation a preparatory task that could have happened beforehand;
- spending medical time on information gathering rather than clinical reasoning.

## 2. Primary users and moment of use

### Patient

After an appointment has already been booked, the patient receives a link and can prepare the consultation with minimal friction.

### Physician

Before seeing the patient, the physician receives a concise clinical vignette describing why the patient is coming, what has changed, what information is available, what is missing and what preparatory work has already been completed.

### Human assistant / care team

Humans are used for actions that automation cannot safely or physically perform: calling in complex situations, retrieving external information, visiting a patient, performing an ECG or vital signs, resolving ambiguous cases.

TwinCare must not require a human assistant to validate every case.

## 3. V0.1 workflow

1. An appointment exists.
2. TwinCare creates a preparation case from a local/manual source or an optional scheduling adapter.
3. The patient receives or is given a secure preparation link.
4. The interaction starts in natural language.
5. TwinCare collects the minimum useful information first.
6. Additional questions are progressively disclosed only when they add clinical or operational value.
7. The patient can upload a photo, image or PDF with minimal friction.
8. Validated clinical instruments may be administered when appropriate.
9. Missing information and preparatory tasks are identified.
10. Tasks may be assigned to a human workflow before the appointment.
11. A short clinical vignette is made available to the physician.
12. For acute/non-programmed care, the preparation case is closed after consultation.

## 4. Clinical vignette — target output

The vignette is the central output of V0.1. It must be short by default and expandable on demand.

Minimum structure:

- patient and appointment context;
- stated reason for consultation;
- relevant changes since the previous known state, when available;
- structured answers / validated score results relevant to this consultation;
- documents available;
- missing or ambiguous information;
- preparatory tasks completed / pending;
- provenance for every clinically relevant fact: patient statement, imported document, locally stored context, human entry, deterministic rule, AI extraction.

The vignette must reduce information. A generated narrative that creates more reading work is a product failure.

## 5. Scope decisions

### KEEP / REFACTOR NOW

- authentication and role concepts;
- patient-facing lightweight interaction shell;
- document upload;
- professional view;
- deterministic state transitions;
- PostgreSQL + Flyway;
- structured questions as a technical capability, after replacement of the current clinical content;
- explicit validation / human intervention states where genuinely needed.

### REFACTOR

- `DossierPatient` concept toward a preparation case linked to an appointment rather than an EHR-like longitudinal dossier;
- `Chatbot` vocabulary toward conversation / consultation preparation;
- question trigger engine toward typed, versioned, testable clinical rules;
- assistant workflow toward an exception/task queue rather than mandatory pre-validation;
- document access toward authenticated, authorization-bound retrieval;
- all provider URLs toward configuration/adapters.

### LATER

- chronic disease longitudinal pathways;
- environmental health module;
- voice interaction;
- deep EHR integrations;
- vendor scheduling APIs;
- automated document extraction and language-model summarization beyond a replaceable local interface;
- automated calendar booking of assistant tasks beyond the internal task model.

### OUT OF V0.1

- autonomous diagnosis;
- autonomous therapeutic decisions;
- opaque clinical prioritization;
- replacement of the EHR/practice-management software;
- a broad patient portal;
- a national medical record;
- a requirement for Doctolib, Weda or any other third-party API to make the core product work.

## 6. Patient experience principles

- One obvious action per screen.
- Natural language first.
- Useful information / perceived effort is the optimization target.
- The first useful capture should take seconds, not minutes.
- Progressive disclosure: a patient who wants to continue may provide more detail without forcing all users through a long form.
- Engagement techniques may improve completion, but time-on-platform is never a success metric.
- Validated questionnaires should be presented with good UX without altering validated wording/scoring requirements when those requirements apply.

## 7. AI boundary

AI is an implementation capability, not the product definition.

Preferred uses behind stable interfaces:

- understanding free text;
- extracting structured candidates from documents;
- classifying an uploaded document;
- producing a draft concise vignette from structured facts.

The deterministic core remains responsible for:

- workflow state;
- rule versions;
- validated score computation;
- task state;
- provenance;
- permissions;
- audit events.

Consequential outputs must remain attributable and reviewable. A model provider must be replaceable by a local implementation without changing the domain model.

## 8. Independence from external vendors

TwinCare must work in a degraded but useful mode with:

- manual appointment creation/import;
- locally stored patient context;
- local document upload;
- internal task queue;
- local clinical vignette generation from deterministic data.

External APIs are optional adapters only. Loss of a vendor integration must not make the core preparation workflow unusable.

## 9. Success measures for the pilot

Measure at minimum:

- invitation-to-start rate;
- preparation completion rate;
- median patient effort/time;
- proportion of consultations with useful pre-consultation information;
- proportion of missing documents identified before consultation;
- preparatory tasks completed before physician encounter;
- physician time saved or interruptions avoided;
- physician perceived preparedness;
- ability to absorb additional patients without extending physician working time;
- safety incidents / misleading information / required corrections.

A feature without measurable benefit on time, cognitive load or clinical quality should not remain in the product.

## 10. Relationship with historical concept documents

The Hacking Health prototype and the e-Soleau deposit document a broader historical concept including pre-consultation, pathways, environmental health, a physical kiosk and a central human-assistant role. They remain important evidence of project history and prior concept work.

They are **not** the current runtime specification. V0.1 deliberately narrows implementation to consultation preparation and changes the assistant from a mandatory validation step to an exception/high-value human actor. This product decision does not rewrite the historical documents.
