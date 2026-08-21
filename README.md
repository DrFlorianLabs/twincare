# TwinCare

TwinCare est une couche de préparation de consultation pour les soins primaires.

## Promesse produit

> Préparer la consultation avant que le patient entre dans le cabinet.

TwinCare intervient après la prise de rendez-vous et avant la consultation. Le patient reçoit un lien, décrit naturellement sa demande, transmet les documents utiles et répond uniquement aux questions nécessaires. TwinCare structure ces informations, identifie les éléments manquants ou les tâches préparatoires et fournit au professionnel une vignette clinique concise.

Le produit vise d'abord à réduire le temps médical perdu à reconstruire le contexte, rechercher des documents et réaliser pendant la consultation des tâches qui auraient pu être anticipées.

## V0.1

Le périmètre de référence est le module amont :

1. rendez-vous déjà pris ;
2. invitation du patient ;
3. recueil conversationnel léger ;
4. dépôt simple de documents ;
5. questions ou instruments cliniques validés lorsque pertinents ;
6. identification des informations manquantes et tâches préparatoires ;
7. intervention humaine uniquement lorsque nécessaire ;
8. vignette clinique courte avant consultation ;
9. clôture après consultation pour l'aigu.

Le suivi longitudinal des maladies chroniques reste un horizon produit ultérieur. Les modules historiques de parcours et de santé environnementale ne pilotent pas la V0.1.

## Principes d'architecture

- **Human-first** : aucune décision diagnostique ou thérapeutique autonome.
- **Local-first** : le coeur fonctionnel ne dépend d'aucune API tierce.
- **Ports / adaptateurs** : agendas, notifications, logiciels métier et moteurs IA sont des intégrations remplaçables.
- **Déterministe au coeur** : états, règles métier, instruments cliniques et tâches sont explicites, versionnés et testables.
- **IA aux frontières** : compréhension du langage, extraction documentaire et synthèse peuvent utiliser une implémentation locale derrière une interface stable.
- **Privacy by design** : aucun document de santé dans Git, aucun contenu de santé dans les logs, accès documentaire authentifié et autorisé.
- **Zéro friction** : le patient donne un maximum d'information utile avec un minimum d'effort perçu.

## Documentation normative

- [`docs/PRODUCT_SCOPE_V0.1.md`](docs/PRODUCT_SCOPE_V0.1.md) — périmètre produit courant.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — architecture cible et frontières.
- [`docs/SECURITY_BASELINE.md`](docs/SECURITY_BASELINE.md) — exigences minimales de sécurité et de confidentialité.
- [`docs/SPRINT_0.md`](docs/SPRINT_0.md) — objectifs et critères de sortie du Sprint 0.
- [`docs/ROADMAP_V0.1.md`](docs/ROADMAP_V0.1.md) — feuille de route active de TwinCare Consultation.
- [`docs/ORGANIZATION_BOOTSTRAP.md`](docs/ORGANIZATION_BOOTSTRAP.md) — amorçage contrôlé d'une organisation sans tenant par défaut.
- [`docs/adr/0001-organization-membership-and-scope.md`](docs/adr/0001-organization-membership-and-scope.md) — décision d'isolation organisationnelle du nouveau module.
- [`docs/adr/0002-patient-invitation-credential-exchange.md`](docs/adr/0002-patient-invitation-credential-exchange.md) — invitation patient à usage unique et session bornée.
- [`docs/adr/0003-patient-session-authentication-and-csrf.md`](docs/adr/0003-patient-session-authentication-and-csrf.md) — authentification de session patient et protection CSRF.
- [`docs/adr/0004-patient-declared-consultation-reason.md`](docs/adr/0004-patient-declared-consultation-reason.md) — motif libre déclaré par le patient et écriture transactionnelle.
- [`docs/adr/0005-patient-preparation-attachment.md`](docs/adr/0005-patient-preparation-attachment.md) — pièce jointe patient bornée, stockage privé et cohérence transactionnelle.
- [`docs/adr/0006-deterministic-readiness-and-professional-view.md`](docs/adr/0006-deterministic-readiness-and-professional-view.md) — règles de préparation déterministes et vignette professionnelle structurée.
- [`docs/adr/0007-human-preparation-tasks-and-ready-confirmation.md`](docs/adr/0007-human-preparation-tasks-and-ready-confirmation.md) — tâches humaines et confirmation médicale liée à la version effectivement relue.
- [`docs/adr/0008-professional-attachment-download.md`](docs/adr/0008-professional-attachment-download.md) — téléchargement professionnel authentifié, vérifié et borné.
- [`docs/adr/0009-professional-context-discovery.md`](docs/adr/0009-professional-context-discovery.md) — découverte serveur des organisations et capacités cliniques du professionnel.

Les prototypes, captures et documents historiques présents dans `artifacts/` documentent l'origine du projet mais ne constituent pas la spécification fonctionnelle courante.

## Structure du dépôt

- `BackEnd/` : Spring Boot / Java 21 / PostgreSQL / Flyway.
- `FrontEnd/app_twin-care/` : interface patient Vue.
- `FrontEnd/back-office_twin-care/` : interface professionnelle Vue.
- `artifacts/` : artefacts historiques de prototype et de présentation.

## Statut

Prototype en consolidation. Ne pas utiliser avec des données réelles de santé tant que les critères de sécurité, de conformité, d'hébergement et de validation clinique nécessaires au contexte de déploiement ne sont pas satisfaits.
