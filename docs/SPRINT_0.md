# TwinCare — Sprint 0

Status: **COMPLETED — 2026-08-21**

Closure evidence:

- product, architecture and security baselines merged through PR #1;
- independent corrective review merged through PR #2;
- PR #2 CI completed successfully for the backend and both frontends;
- backend verification rerun from the merged source: 43 tests passed;
- no Sprint 1 feature was mixed into the corrective Sprint 0 pull request.

Residual items listed below are release gates for a real-patient pilot, not
unfinished Sprint 0 scope. The historical patient/runtime PDF remains excluded
from the current tree; its possible removal from Git history requires a separate
controlled investigation without opening or reusing the file.

Goal: **make the repository safe and conceptually stable enough to build the real V0.1 without amplifying prototype debt.**

## Sprint exit criteria

Sprint 0 is complete when:

- product scope V0.1 is explicit and versioned in the repo;
- historical modules can no longer be mistaken for current priorities;
- public document access is removed;
- document access is authorization-bound;
- runtime uploads are excluded from source control;
- default/weak JWT configuration is removed;
- obvious authentication escalation/reset flaws are closed;
- clinical trigger logic fails closed and the known recurrence bug is fixed;
- provider URLs are configuration, not product logic;
- a first automated security/rule test suite exists;
- the next implementation backlog is organized around the consultation-preparation flow.

## Workstream A — Product boundary

### Done in this sprint branch

- Define consultation preparation as the V0.1 product.
- Make the clinical vignette the principal professional output.
- Define the assistant/human role as exception/high-value action rather than mandatory validation.
- Keep chronic pathways as later scope.
- Park environmental health for later.
- Record that historical Hacking Health/e-Soleau artefacts remain project-history evidence, not current implementation requirements.

## Workstream B — Security blockers

### Immediate changes

- Remove public file serving by opaque filename.
- Fetch document bytes by document ID after authorization.
- Verify upload file signatures and generate storage keys server-side.
- Remove health-document upload artifacts from the branch and ignore runtime upload directories.
- Remove identifying information from critical logs.
- Require explicit JWT secret.
- Restrict account creation/reset behavior.
- Enforce privileged account and sensitive-object checks at the service boundary,
  in addition to HTTP route rules.
- Coordinate document bytes with database transactions: uploads are removed on
  rollback, deletions are quarantined until commit, and interrupted cleanups are
  reconciled from document metadata at startup.

### Corrective review gates

- Malformed bearer tokens produce an authentication failure rather than a
  generic server error.
- Security integration tests exercise anonymous access, cross-patient access,
  privileged endpoints and deletion of patient document bytes.
- A dedicated Flyway test verifies that baselining a populated legacy schema at
  version 4 preserves existing data instead of replaying destructive prototype
  migration V3.
- The back-office development proxy is local/configurable and contains no
  mandatory hosted-provider URL.
- Maven, npm and GitHub Actions dependency updates are monitored weekly.

### Still required before real-patient pilot

- Organisation/tenant authorization model.
- Formal audit event model.
- Secure patient invitation/token lifecycle tied to a preparation case.
- Threat model and penetration/security review.
- HDS/deployment architecture and operational controls.
- Retention/deletion policy and backup/restore tests.

## Workstream C — Clinical engine

### Current prototype

The existing trigger engine is a prototype asset only. Current question content was not validated as production clinical logic.

### Sprint 0 rule

- Unknown triggers must fail closed.
- Broken recurrence parsing is fixed/tested.
- No new clinical question content is added to the legacy engine.
- Historical environmental-health beans are excluded from the default runtime;
  their profile remains legacy-only and the routes stay denied.

### Next implementation

Replace string triggers with typed/versioned rules and build a separate catalog for validated instruments. Clinical content requires explicit medical validation and source/version metadata.

## Workstream D — Provider independence

Core V0.1 must be demonstrable without Doctolib/Weda/vendor APIs:

- manual/local appointment creation;
- locally generated preparation link;
- patient interaction;
- document upload;
- internal task queue;
- vignette.

Adapters are added only after this path works and creates measurable value.

## Feature disposition

| Area | Decision | Sprint 0 treatment |
|---|---|---|
| Auth / roles | KEEP + REFACTOR | close privilege/reset issues |
| Document upload | KEEP + REFACTOR | secure boundary |
| Professional views | KEEP + REFACTOR | converge toward vignette + task exceptions |
| Patient UI shell | KEEP + REFACTOR | simplify toward conversational preparation |
| Current chatbot questions | REFACTOR | no clinical expansion |
| String trigger engine | REPLACE | fail-closed safety patch now |
| `DossierPatient` | REFACTOR | migrate semantics toward one preparation episode |
| Assistant validation workflow | REFACTOR | exception queue, not mandatory gate |
| Parcours | LATER | chronic longitudinal scope |
| Environnement | LATER | no V0.1 work |
| Third-party API integrations | LATER/ADAPTER | never core dependency |
| Autonomous diagnosis/therapy | DELETE FROM PRODUCT SCOPE | prohibited |

## Next sprint candidate — Vertical Slice 1

Build one complete, vendor-independent path:

1. create a preparation case manually;
2. create a short-lived patient invitation;
3. patient submits free-text reason;
4. patient optionally uploads one document;
5. deterministic workflow records missing/available information;
6. professional sees a concise vignette/read model;
7. case closes explicitly.

No LLM is required to validate this slice. A language-understanding port can initially return the raw patient wording and be replaced later.

## Definition of useful

A vertical slice is accepted only if we can answer:

- What concrete problem does it solve?
- For which user?
- At what moment in the care workflow?
- What measurable time/cognitive/quality gain should it create?
- How does it integrate without requiring the practice to replace existing software?

If those answers are weak, the feature does not enter the sprint.
