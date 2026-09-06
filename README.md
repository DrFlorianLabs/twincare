# TwinCare — préparation de consultation

**Le patient raconte ce qu'il veut aborder et transmet ses documents. TwinCare
prépare une fiche courte, sources à l'appui ; le médecin vérifie et conserve les
éléments utiles dans son logiciel métier.**

Le premier usage est la préparation d'une consultation déjà planifiée. TwinCare
ne remplace ni le dossier médical ni Doctolib. Il ne pose pas de diagnostic et ne
recommande pas de traitement. Nous réutilisons le socle existant ; pas de reprise
à zéro ni de plateforme générique à construire avant de tester cet usage.

## Comprendre le projet — ordre de lecture

| Lecteur / besoin | Document de référence |
| --- | --- |
| Comprendre le fonctionnement et le présenter aux collègues | [Concept opérationnel](docs/CONCEPT_OPERATIONNEL.md) : contact patient, Doctolib sans API, collecte, stockage et contrôle humain. |
| Savoir ce qui existe, ce qu'on construit ensuite et quand s'arrêter | [Feuille de route](docs/ROADMAP.md) : étapes, critères de validation et verrous avant données réelles. |
| Développer ou reprendre le code | [AGENTS.md](AGENTS.md), puis [architecture](docs/ARCHITECTURE.md) et [sécurité](docs/SECURITY_BASELINE.md). |

Le [support Word pour les associés](docs/presentation/TwinCare_Concept_Operationnel_2026-09.docx)
est une proposition datée du 6 septembre 2026. Les documents Markdown ci-dessus
font foi pour l'état du dépôt et les prochaines étapes. La
[note de reprise](docs/REPRISE_2026-09.md) conserve l'historique de l'incrément de
fiche clinique ; elle ne constitue plus la feuille de route.

## Ce qui existe sur main / ce qui reste à construire

| Sujet | Disponible dans le code | Cible, non encore disponible |
| --- | --- | --- |
| Entrée patient | Cas créé par l'équipe, invitation individuelle à usage unique, session limitée au cas. | Lien générique dans les consignes Doctolib, vérification du téléphone et dépôt sans rattachement préalable. |
| Recueil | Motif libre corrigible ; une pièce jointe PDF/JPEG/PNG, 10 Mio maximum. | Parcours de récit guidé, plusieurs pièces si leur utilité est confirmée ; voix ensuite seulement. |
| Préparation | Fait « motif » avec provenance, dates, historique et revue ; tâches ; vignette déterministe et export JSON. | OCR, extraction avec référence à la page, brouillon documentaire automatisé. Aucun modèle IA actif aujourd'hui. |
| Relecture / transfert | Confirmation médicale, audit et clôture ; copie texte volontaire dans l'espace professionnel Vue. | Rapprochement d'identité tracé et confirmation explicite du versement dans le logiciel métier. Copier ne prouve pas ce versement. |
| Exploitation | Démonstration locale isolée ; backend normal PostgreSQL à configurer. | Sas distant HDS, application locale chiffrée et exploitée, sauvegarde restaurable, politique de conservation appliquée. |

La vignette actuelle assemble le motif, les tâches et la liste des documents
reçus **sans lire leur contenu**. Elle exclut le motif rejeté du texte utilisable,
bloque alors la confirmation et revérifie la version avant copie ou export.
Le JSON conserve l'historique avec ses statuts : il n'est pas anonyme.

Il n'y a ni API Doctolib, ni lecture d'écran, ni synchronisation d'agenda, ni
relance automatique des non-répondants. La configuration d'un lien externe dans
les consignes doit être testée sur le compte du cabinet ; elle ne connecte pas
les dossiers patients à TwinCare.

> **Données fictives uniquement.** La démonstration locale efface et recrée son
> scénario synthétique. N'y saisir aucune donnée réelle.

## Essai local sur Mac

Prérequis : macOS et Java 21. Aucun compte, secret, Docker, base externe ou
service cloud n'est nécessaire pour le scénario. Au premier lancement, le
téléchargement des dépendances demande une connexion Internet ; sa durée dépend
du poste et du réseau.

1. Double-cliquer sur **Lancer TwinCare Demo.command**. Au premier lancement,
   macOS peut demander un clic droit puis **Ouvrir**.
2. Le navigateur ouvre <http://127.0.0.1:8088/demo/>.
3. Dans **Vue patient**, saisir ou choisir un motif fictif, puis l'envoyer et le
   corriger une fois.
4. Dans **Vue équipe de soins**, contrôler la valeur actuelle, sa date, sa
   provenance, son degré de certitude et l'historique, puis confirmer ou rejeter
   le fait.
5. Exporter le contexte JSON v1. Utiliser **Réinitialiser la démo** pour rejouer
   le scénario et **Arrêter la démo** en fin d'essai.

Cette démo intégrée illustre le cycle du motif et son historique. La vignette
texte avec tâches et documents est disponible dans l'espace professionnel Vue,
qui utilise le backend normal et ses comptes autorisés.

Les trois commandes Mac à la racine servent respectivement à lancer, arrêter et
réinitialiser la démonstration. Le lancement écoute uniquement sur la boucle
locale, utilise une base H2 isolée et génère ses secrets éphémères sans les
afficher.

## Architecture en bref

- `BackEnd/` : monolithe modulaire Java 21 / Spring Boot. Le noyau
  `clinicalcontext` sépare domaine, application, persistance et API.
- `FrontEnd/app_twin-care/` : parcours patient Vue 3 sécurisé et borné au cas.
- `FrontEnd/back-office_twin-care/` : espace professionnel Vue 3.
- `BackEnd/src/main/resources/static/demo/` : interface d'essai intégrée, sans
  dépendance Node ni service externe.
- `docs/ARCHITECTURE.md` : frontière produit, modèle et décisions durables.
- `docs/SECURITY_BASELINE.md` : règles de sécurité et de confidentialité.

La démonstration utilise le même domaine, les mêmes migrations et la même
persistance de faits cliniques que l'application. Seuls l'autorisation et le jeu
de données sont remplacés par des adaptateurs locaux actifs sous le profil
`demo`.

## Développement

Interfaces : Node 22 (`.nvmrc`) et npm 10.9.9, comme dans la CI.

Backend : Java 21 doit être installé et sélectionné dans `JAVA_HOME`.

```sh
cd BackEnd
sh ./mvnw clean verify
```

Sur macOS, on peut sélectionner Java 21 pour cette commande avec
`JAVA_HOME=$(/usr/libexec/java_home -v 21) sh ./mvnw clean verify`.

Interface patient :

```sh
cd FrontEnd/app_twin-care
npm ci
npm test
npm run type-check
npm run build
```

Interface équipe de soins :

```sh
cd FrontEnd/back-office_twin-care
npm ci
npm run test:unit
npm run build
```

L'application normale exige des secrets explicites et une base PostgreSQL. Le
profil `demo` est le seul à embarquer H2 et refuse son initialisation s'il n'est
pas lié à `127.0.0.1` ou si sa source de données n'est pas la base de démo
attendue.

Toute contribution doit respecter `AGENTS.md`. Les fixtures doivent rester
strictement synthétiques et aucun contenu patient ou identifiant ne doit être
journalisé.

Les tests et builds sont définis dans [.github/workflows/ci.yml](.github/workflows/ci.yml).
Une CI verte ne remplace ni un essai dans le navigateur ni les conditions d'un
pilote réel décrites dans la feuille de route. La présence sur `main` ne signifie
pas que l'application a été déployée.
