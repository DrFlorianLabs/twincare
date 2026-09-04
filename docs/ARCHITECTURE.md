# Architecture TwinCare V0.1

Statut : document normatif. Dernière mise à jour : 4 septembre 2026.

## Frontière produit et test de valeur

TwinCare prépare une consultation déjà réservée, entre la prise de rendez-vous
et l'entrée du patient chez le médecin. Il réduit le temps perdu à retrouver un
motif fiable, des documents et les actions de préparation encore ouvertes.

La première tranche du Clinical Context Engine répond au test produit suivant :

- **problème** : le motif patient corrigé perd facilement son origine, sa date
  ou la trace de la valeur qu'il remplace ;
- **utilisateurs** : patient contributeur, assistant médical et médecin ;
- **moment** : collecte en amont, puis revue juste avant la consultation ;
- **gain visé** : moins de ressaisie et de vérifications, avec une meilleure
  qualité du contexte présenté ;
- **intégration** : API et export JSON portables, sans imposer le remplacement du
  logiciel métier.

V0.1 n'est ni un EHR, ni un portail patient généraliste, ni un moteur de
diagnostic, ni une plateforme d'agents. Les termes et règles cliniques non
validés restent hors du produit.

## Forme générale

Le backend est un monolithe modulaire Java 21 / Spring Boot. Les modules suivent
une séparation domaine, application, ports, adaptateurs de persistance et API.
Les fournisseurs externes restent derrière des ports appartenant au produit ;
le parcours essentiel dispose d'un fonctionnement local et manuel.

`PreparationCase` représente un épisode de préparation pour une consultation,
pas le dossier médical longitudinal d'une personne. Il porte une organisation,
une référence patient opaque, une référence de rendez-vous facultative, une
échéance facultative, un état et une révision monotone.

Les concepts historiques `DossierPatient`, chatbot et administration de comptes
ne doivent pas être étendus. Leurs contrôleurs sont absents de l'exécution V0.1
normale et enfermés derrière le profil inactif `legacy-prototype`.

## Modules actifs

### Préparation de consultation

Le module `backend.preparation` possède le cas, les invitations et sessions
patient, le motif déclaré, la pièce jointe bornée, les tâches humaines, la
lecture professionnelle, la confirmation de préparation, l'audit et la clôture.

Les transitions sont explicites. Une contribution patient n'est acceptée que si
le cas, l'invitation et la session concordent et restent actifs après acquisition
des verrous. Toute mutation pertinente incrémente la révision du cas.

### Clinical Context Engine

Le module `backend.clinicalcontext` transforme une donnée concrète du wedge — le
motif déclaré par le patient — en faits cliniques atomiques réutilisables. Il ne
contient aucune règle diagnostique.

Un `ClinicalFact` comprend :

- un identifiant opaque et le contexte de consultation propriétaire ;
- un type explicite et une valeur typée ;
- une période clinique optionnelle (`effectiveFrom`, `effectiveTo`) distincte de
  l'instant de recueil (`recordedAt`), sans inventer une date absente ;
- une provenance créée par le serveur ;
- une certitude explicite, ici `NOT_ASSESSED` ;
- un cycle de vie `CURRENT` ou `SUPERSEDED` ;
- une revue humaine `UNREVIEWED`, `CONFIRMED` ou `REJECTED`, avec instant et
  référence de relecteur opaque.

La réécriture du même motif est idempotente. Une correction crée un nouveau fait
courant et conserve l'ancien comme remplacé dans la même transaction que la
mise à jour du cas et l'événement d'audit. Deux faits courants du même type pour
un même contexte sont interdits par le domaine et la persistance.

La revue humaine est enregistrée sur le fait et ne peut être remplacée : une
correction exige une nouvelle version. Elle incrémente aussi la révision du cas
et invalide toute confirmation de préparation antérieure. Elle n'apparaît pas
encore dans la chronologie d'audit des actions de préparation.

Cette tranche couvre un seul type de fait et un contexte par consultation. La
fusion longitudinale multi-documents, les relations de contradiction et
l'ingestion automatisée restent à implémenter ; le domaine est indépendant de
Spring, mais les services de lecture et de revue restent adaptés au cas amont.

L'export `twincare.clinical-context.v1` est un DTO stable et portable. Il expose
les faits et leurs métadonnées utiles, jamais la référence patient ni
l'organisation. Le contrat pourra devenir un adaptateur vers TwinCare, MedFlow
ou CareOS sans introduire aujourd'hui une plateforme générique.

## Préparation déterministe

La décision opérationnelle de préparation professionnelle reste distincte de la
complétude du seul contexte atomique exporté.

La politique de préparation est pure, versionnée et déterministe. Elle produit
un état parmi `INCOMPLETE`, `ACTION_REQUIRED`, `REVIEWABLE`, `READY` ou
`NOT_APPLICABLE`, avec le jeu de règles et les preuves qui l'expliquent. Elle
n'interprète pas le texte libre et ne dépend pas d'un LLM. Une incohérence ou
une règle inconnue échoue fermée.

Les tâches humaines sont bornées, typées et idempotentes. Leur création,
affectation et résolution prennent le verrou du cas puis modifient sa révision.
Le navigateur ne choisit jamais l'acteur effectif : il est dérivé de l'identité
authentifiée et de l'appartenance active.

Seul un médecin peut confirmer qu'un cas est prêt. La confirmation lie une
preuve immuable à la révision effectivement relue. Toute modification ultérieure
du motif, des tâches ou des pièces jointes invalide cette preuve sur les lectures
suivantes.

## Accès patient

Une invitation est un justificatif d'amorçage à usage unique pour un seul cas.
Son secret et celui de la session sont conservés sous forme de condensats HMAC
séparés par domaine. L'échange atomique consomme l'invitation et crée une session
courte.

Le patient utilise un cookie `HttpOnly` dans une chaîne de sécurité dédiée, sans
JWT. Le serveur déduit l'organisation, le cas et la portée ; les écritures
vérifient aussi le CSRF. La reprise du motif renvoie le minimum exact nécessaire
depuis cette même portée et reste en mémoire volatile dans le navigateur.

La pièce jointe patient est un objet distinct, privé, limité à un PDF, JPEG ou
PNG de 10 Mio maximum. L'adaptateur de stockage met les octets en quarantaine,
coordonne promotion ou compensation avec la transaction et réconcilie les
opérations interrompues au démarrage.

## Accès professionnel

Le contexte professionnel — organisations actives, rôles et capacités — est
dérivé côté serveur. Le tenant choisi par le navigateur est indicatif ; chaque
cas d'usage réautorise la ressource.

L'espace professionnel suit un seul parcours responsive : liste bornée, détail
structuré, tâches, téléchargement protégé, audit, confirmation puis clôture. Les
lectures composites utilisent un instantané cohérent et la préparation affichée
provient de la politique serveur.

La création manuelle d'un cas est idempotente dans une organisation par UUID de
requête et empreinte canonique du contenu. Un rejeu identique ne recrée ni cas ni
audit ; un contenu différent avec le même UUID échoue. La création et l'émission
de l'invitation sont deux capacités séparées. Le lien patient est remis dans le
fragment d'URL, copié explicitement et jamais persisté par le frontend.

Le téléchargement professionnel autorise d'abord l'appartenance clinique, puis
verrouille le cas et la pièce jointe. L'adaptateur ouvre une seule fois l'objet
final sans suivre les liens symboliques, vérifie taille et SHA-256 sur ce handle,
termine la transaction, puis diffuse ce même handle.

La clôture est réservée au médecin. Elle verrouille cas, invitations et sessions,
exige la révision et la preuve de préparation exactes, enregistre une preuve
immuable et révoque tous les accès patient dans une seule transaction. Un rejeu
identique est sans effet ; une fermeture contradictoire échoue.

## Audit

La chronologie métier est append-only et séparée des logs techniques. Chaque
événement utilise un type fermé, une version de schéma, un instant serveur, un
acteur catégorisé, le périmètre organisation/cas, la révision persistée et au
plus un identifiant technique de sujet.

La mutation et son événement partagent la même transaction. Un échec d'audit
annule la mutation ; un rejeu idempotent ou un no-op n'ajoute pas de preuve. Les
lectures sont réservées au médecin, bornées, paginées par curseur et signalent
explicitement un historique partiel pour les cas antérieurs à l'audit.

## Surface HTTP V0.1

La sécurité applique une liste positive méthode + chemin, terminée par
`denyAll`. Les familles actives sont :

- connexion professionnelle et découverte de son contexte ;
- cas de préparation, tâches, confirmation, clôture et audit ;
- création manuelle, invitations et téléchargement de pièce jointe ;
- échange d'invitation et parcours patient borné ;
- lecture et revue des faits du contexte clinique.

L'administrateur d'organisation n'est pas un super-utilisateur clinique. Les API
de prototype, la documentation interactive et toute route inconnue restent
inaccessibles, même avec un rôle authentifié.

## Persistance et migrations

Flyway est l'unique mécanisme de changement de schéma. Les migrations V3 à V12
construisent l'isolation organisationnelle, le parcours patient, les tâches,
l'audit, la clôture et l'idempotence de création. V13 ajoute `clinical_fact` et
reprend le motif courant existant avec sa provenance démontrable.

Le domaine ne dépend pas de JPA. Les entités et repositories Spring Data restent
dans les adaptateurs. Les écritures concurrentes utilisent verrous explicites,
contraintes uniques et révisions optimistes selon le cas d'usage.

## Démonstration locale

Le profil `demo` remplace uniquement les frontières d'identité et de scénario.
Il utilise le domaine, les services, Flyway, JPA et le contrat d'export réels avec
des données strictement synthétiques. Un traitement d'environnement antérieur à
l'initialisation de la datasource refuse toute adresse autre que `127.0.0.1` et
toute URL autre que la base H2 isolée autorisée.

La démonstration n'est pas un mode de production : elle n'a aucun fournisseur
externe, ne charge aucune ressource distante et réinitialise son contexte borné.
