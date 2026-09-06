# TwinCare — préparation de consultation

TwinCare aide le patient et l'équipe de soins à préparer une consultation déjà
planifiée. La tranche actuelle recueille le motif de consultation, conserve ses
corrections avec leur provenance, permet une validation humaine et exporte un
contexte clinique portable. Elle ne pose pas de diagnostic, ne recommande pas de
traitement et ne remplace pas le logiciel métier.

L'espace professionnel affiche le motif avec sa source, sa date, sa revue et ses
corrections. La vignette texte rassemble ces informations, les documents reçus
et les tâches pour un transfert manuel dans le logiciel métier. Un motif rejeté
est exclu du texte utilisable et bloque la confirmation de préparation. La copie
et l'export vérifient la version relue.

Le [point de reprise de septembre](docs/REPRISE_2026-09.md) explique les choix,
les limites et les prochaines étapes proposées.

> **Données fictives uniquement.** La démonstration locale efface et recrée son
> scénario synthétique. N'y saisir aucune donnée réelle.

## Essai local sur Mac — moins de 10 minutes

Prérequis : macOS et Java 21. Aucun compte, secret, Docker, base externe ou
service cloud n'est nécessaire.

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

Backend :

```sh
cd BackEnd
JAVA_HOME=$(/usr/libexec/java_home -v 21) sh ./mvnw clean verify
```

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
