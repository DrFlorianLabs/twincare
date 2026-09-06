# Architecture TwinCare V0.1

Statut : document normatif. Dernière mise à jour : 6 septembre 2026.

Lire d'abord le [concept opérationnel](CONCEPT_OPERATIONNEL.md) et la
[feuille de route](ROADMAP.md). Les sections ci-dessous décrivent les mécanismes
présents dans le code ; la section finale « Cible non implémentée » décrit les
frontières à construire, pas des garanties déjà fournies.

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

Le module `backend.clinicalcontext` transforme une donnée du premier usage — le
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
et ajoute un événement `CLINICAL_FACT_CONFIRMED` ou `CLINICAL_FACT_REJECTED`, avec
l'acteur et l'identifiant du fait, dans la même transaction. Un rejeu identique
n'incrémente ni la révision ni l'audit. Après confirmation de préparation,
une nouvelle revue est refusée pour conserver la preuve figée ; le rejeu
identique reste possible. Le parcours de réouverture reste hors de cette tranche.

Cette tranche couvre un seul type de fait et un contexte par consultation. La
fusion longitudinale multi-documents, les relations de contradiction et
l'ingestion automatisée restent à implémenter ; le domaine est indépendant de
Spring, mais les services de lecture et de revue restent adaptés au cas amont.

L'export `twincare.clinical-context.v1` est un DTO stable et portable. Il expose
les faits et leurs métadonnées utiles, jamais la référence patient ni
l'organisation. Son champ additif `preparationRevision` identifie la version du
cas correspondant aux faits et au document. La lecture prend le même verrou de
cas que les mutations, dans une transaction courte sans modifier les données ;
elle garantit ainsi un instantané cohérent sur PostgreSQL et H2, au prix d'une
sérialisation des opérations sur un même cas pendant cette lecture. Aucun appel
externe ni rendu d'interface n'est réalisé sous ce verrou.

Le contrat pourra devenir un adaptateur vers TwinCare, MedFlow
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

Un motif courant rejeté interdit la confirmation, côté serveur et sous le verrou
du cas. La préparation interroge un port `CurrentConsultationReasonReview`
adapté au moteur de contexte. Une ancienne version rejetée n'interdit pas une
nouvelle préparation sur un motif corrigé. La revue atomique n'est pas une étape
obligatoire ajoutée à toutes les consultations.

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

Il s'agit d'un accès à un cas créé et désigné par l'équipe, pas d'un portail
générique de dépôt. La présence d'une invitation n'établit pas à elle seule
l'identité médicale ; il n'existe actuellement ni vérification SMS intégrée ni
file de rapprochement d'identité.

## Accès professionnel

Le contexte professionnel — organisations actives, rôles et capacités — est
dérivé côté serveur. Le tenant choisi par le navigateur est indicatif ; chaque
cas d'usage réautorise la ressource.

L'espace professionnel suit un seul parcours responsive : liste bornée, détail
structuré, tâches, téléchargement protégé, audit, confirmation puis clôture. Les
lectures composites utilisent un instantané cohérent et la préparation affichée
provient de la politique serveur.

La carte du motif utilise le contexte clinique : version courante, provenance,
certitude, revue et historique replié. La copie texte assemble les informations
enregistrées et les tâches, sans interprétation. Les anciennes versions et le
motif rejeté sont exclus de la vignette utilisable ; l'export JSON composite
conserve l'historique et les statuts explicites. Ce composite inclut la référence
patient opaque de la préparation. Il n'est donc pas un export anonyme.
Un document reçu est présenté comme reçu, sans prétendre en avoir analysé le contenu.

Avant toute copie ou export, l'interface relit les deux vues et vérifie leur
révision, leur périmètre et leur contenu. Une divergence, un accès retiré ou un
contrat inconnu bloque l'export et impose une actualisation. Les résultats tardifs
ne doivent pas réapparaître après changement de patient ou d'organisation.
Cette vérification porte sur l'instantané relu ; elle ne promet pas une
synchronisation continue avec le logiciel métier après la copie.

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

V14 étend les contraintes d'audit aux revues de faits cliniques. Les anciennes
migrations restent inchangées.

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

## Cible non implémentée — frontières de la prochaine réalisation

Conserver le monolithe et ses contrats utiles. La séparation décrite ici relève
des responsabilités, pas d'une demande de nouveaux microservices. Les noms
ci-dessous sont des concepts de conception ; ce ne sont pas encore des classes,
tables, routes ou états exposés par l'application.

| Frontière cible | Responsabilité | Invariant à tester |
| --- | --- | --- |
| Dépôt non rattaché | Recevoir déclaration, récit et pièces dans une organisation, sans association à un patient existant. | Lien générique sans secret patient ; aucune recherche ou exposition d'un dossier au déclarant ; séparation des sessions. |
| Décision de rapprochement | Un professionnel habilité associe un dépôt au bon patient et à la consultation ; doute et rejet restent explicites. | Auteur, instant et justification structurée tracés sans texte clinique dans l'audit ; doublon traité explicitement ; aucune fusion sur nom/date seulement. |
| Transfert sas / local | Récupération sortante authentifiée, contrôle d'intégrité, réception durable et sauvegarde, accusé puis purge. | Reprise idempotente après panne ; pas de purge sur simple succès HTTP ; échec visible et traité avant échéance. |
| Traitement de sources | Extraction/OCR isolé avec limites de ressources, éléments sourcés, version de traitement et brouillon optionnel. | Pas d'appel long sous verrou de cas ; résultat lié à la version exacte de la source ; résultat périmé non promu ; aucun effet clinique du modèle. |
| Versement au dossier métier | Action explicite du médecin après vérification d'identité et transfert des éléments et pièces utiles. | Copie, confirmation de préparation, versement et clôture restent des événements distincts ; la purge ne dépend pas du seul bouton Copier. |

Séparer trois dimensions : état technique du transfert, décision de rattachement
et état clinique de préparation. Un dépôt peut être réceptionné durablement mais
non rattaché ; il peut être rattaché sans être relu. Ne pas faire signifier à un
unique booléen « prêt » ces trois choses différentes.

Les coordonnées déclarées nécessaires au rapprochement restent dans un espace
protégé distinct de la référence patient opaque du cas. Le téléphone vérifié ne
devient ni identifiant national de santé ni preuve d'identité. L'identitovigilance
et les besoins éventuels d'INS doivent être qualifiés pour le parcours retenu.

Le sas distant, le connecteur de récupération et le traitement local devront
partager des contrats versionnés, des limites et des preuves techniques minimales.
L'organisation est dérivée de la configuration authentifiée, jamais d'un champ
patient faisant autorité. L'anti-abus ne doit pas envoyer les données de santé à
un service tiers non validé.

Le moteur de sources conserve document, passage/page si disponible, date
documentaire, date de réception, statut de traitement et version. Un modèle
produit seulement une proposition ; le domaine valide le contrat, conserve les
sources et laisse la revue au médecin. Le fait actuel `CONSULTATION_REASON`
n'est pas détourné pour stocker un dossier entier ou un JSON médical libre.

La cible d'exploitation est sas HDS en France + application locale gérée +
sauvegarde séparée + dossier métier de référence. Elle n'est pas obtenue en
exposant le profil `demo` sur Internet. Chiffrement, exploitation, confirmation
de versement et conservation restent des travaux dédiés du [plan](ROADMAP.md).
