# Reprise TwinCare — septembre 2026

> Note historique de l'incrément intégré par la [PR #50](https://github.com/DrFlorianLabs/twincare/pull/50).
> Pour les décisions actuelles, lire le [concept opérationnel](CONCEPT_OPERATIONNEL.md)
> et la [feuille de route](ROADMAP.md). Les pistes de fin de note décrivent la
> réflexion de cet incrément ; elles ne constituent pas un backlog parallèle.

## Décision de travail

Faire de la préparation de consultation le premier usage utile. Le moteur de
contexte devient réutilisable à mesure que cet usage le justifie. Le dépôt
possède déjà une fondation de faits cliniques : la priorité est de la rendre
exploitable par les soignants.

Les discussions récentes imposent l'ordre **questionner → supprimer → simplifier
→ accélérer → automatiser**. La synthèse stratégique du 5 septembre reste une
proposition à discuter ; ses ambitions commerciales et son scénario aval ne
valent pas validation d'un nouveau périmètre.

## Ce qui a été questionné

| Question | Choix pour cet incrément |
| --- | --- |
| Faut-il reconstruire un moteur « Runtime » ? | Réutiliser clinicalcontext, déjà présent. |
| Faut-il un nouvel écran et une nouvelle saisie professionnelle ? | Remplacer la carte du motif par une vignette issue des données existantes. |
| Faut-il une IA pour restituer le motif et les actions ? | Une restitution déterministe suffit ici. Aucun modèle ni abonnement ajouté. |
| Le patient doit-il connaître son motif médical ? | L'aider à raconter avec ses mots ce qui le préoccupe, depuis quand et ce qu'il attend. |
| Un motif reçu signifie-t-il « consultation prête » ? | Distinguer recueil, revue du fait et confirmation de préparation. |
| Comment s'intégrer au logiciel métier aujourd'hui ? | Copie texte volontaire et export structuré ; aucune connexion Doctolib/Weda/DMP présumée. |

Les fonctionnalités historiques de borne, portail élargi, parcours aval et santé
environnementale restent hors de cette tranche. Les documents historiques et
éléments de propriété intellectuelle sont conservés.

## Test de valeur du changement

- **Problème :** retrouver la dernière formulation, vérifier sa source et
  reconstituer manuellement les actions avant la consultation.
- **Utilisateurs :** médecin et assistant médical, chacun dans ses droits.
- **Moment :** préparation puis relecture juste avant de recevoir le patient.
- **Gain à mesurer :** temps total de lecture/correction/transfert, recherches
  supplémentaires et erreurs de version. Aucun gain chiffré n'est encore démontré.
- **Intégration :** utiliser les informations déjà recueillies, conserver le
  logiciel métier et transférer un texte lisible en une action explicite.

## Incrément

La vue professionnelle présente le motif courant, la provenance, la date, la
certitude et la revue séparément. Les anciennes formulations restent consultables
dans un historique replié. La vignette copiée conserve les statuts et les sources,
inclut les tâches et signale les documents reçus sans prétendre les avoir lus.
Les anciennes versions et le motif rejeté ne sont pas présentés comme utilisables.

La revue ajoute une trace d'audit sans texte clinique. Une modification de la
préparation détectée pendant la lecture bloque l'export de l'ancienne vue. Un motif
courant rejeté bloque la confirmation côté serveur. Après confirmation de
préparation, une nouvelle revue est refusée : un parcours de réouverture devra
être conçu explicitement si le terrain le demande.

## Limites actuelles

- Le moteur couvre **un type de fait, le motif, dans un contexte de consultation**.
  Ce n'est pas encore une reconstruction longitudinale de dossier.
- Il ne lit pas automatiquement les documents et ne construit pas d'anamnèse,
  de diagnostic ou de traitement.
- La copie n'écrit pas dans le logiciel métier et ne vérifie pas l'identité du
  patient qui y est ouvert. Le professionnel vérifie cette correspondance.
- Les exports contiennent des informations cliniques. Le JSON conserve
  volontairement les faits historiques et rejetés avec leurs statuts.
- La démonstration utilise exclusivement des données fictives. Cette reprise
  ne constitue pas une autorisation de pilote réel ni une qualification de
  l'hébergement, du chiffrement au repos ou de la conformité réglementaire.

## Pistes proposées lors de cet incrément — historique

1. **Éprouver le parcours sur quelques cas fictifs.** Comparer lecture et transfert
   avec la pratique habituelle. Mesurer le temps de correction, pas seulement
   la vitesse d'affichage. Supprimer les métadonnées ou étapes qui n'aident pas.
2. **Choisir une première source documentaire réellement accessible.** Observer
   qui la récupère, dans quel format et à quel moment. Ne pas coder un connecteur
   sur l'hypothèse qu'une API existe.
3. **Structurer un seul type d'information supplémentaire utile.** Source,
   date et passage justificatif obligatoires ; correction humaine et incertitude
   conservées. Choisir ce type après l'essai, sans figer un schéma médical inventé.
4. **Décider ensuite quoi automatiser.** Retenir un traitement seulement s'il
   améliore le temps total ou la qualité après vérification humaine.

L'ambition d'une infrastructure française puis européenne demeure compatible
avec cette trajectoire. Elle doit reposer sur des données fiables, des interfaces
portables et des coûts d'exploitation mesurés, avant multiplication des modules.
