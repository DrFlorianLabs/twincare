# Socle de sécurité et de confidentialité

Statut : règles normatives pour tout environnement TwinCare.

Les protections existantes décrites ici ne constituent pas une homologation.
Le [concept](CONCEPT_OPERATIONNEL.md) fixe la cible ; la [feuille de route](ROADMAP.md)
liste les preuves encore nécessaires avant données réelles. Les exigences futures
en fin de document ne sont pas présentées comme déjà implémentées.

## Principes non négociables

- Ne jamais committer de donnée de santé réelle, export patient, document
  clinique, secret, fichier `.env` ou donnée d'exécution.
- Ne jamais journaliser le contenu saisi, l'identité d'un patient, un nom de
  fichier clinique, un jeton, une charge d'export ou l'identité d'un relecteur.
- Minimiser la collecte et conserver provenance, temporalité, certitude et
  validation de toute information cliniquement pertinente.
- Authentifier puis autoriser chaque objet sensible à la frontière du cas
  d'usage. UUID, clé de stockage et donnée fournie par le navigateur ne sont pas
  des preuves d'accès.
- Échouer fermé pour toute règle, portée, état, type de contenu ou transition
  inconnus.
- Considérer toute sortie d'IA comme une entrée non fiable ; elle ne peut ni
  valider un fait, ni confirmer un cas, ni produire seule un effet clinique.

## Secrets et configuration

Les secrets JWT, mots de passe initiaux, peppers d'invitation/session et accès à
la base sont obligatoires hors démonstration. Les fichiers d'exemple ne
contiennent que des marqueurs non fonctionnels. Les valeurs viennent du
gestionnaire de secrets de l'environnement, sont renouvelables et ne sont jamais
affichées par l'application ou les scripts.

Le profil `demo` génère des secrets éphémères, écoute uniquement sur
`127.0.0.1` et n'accepte que sa base H2 dédiée. Cette vérification s'exécute avant
l'initialisation de la datasource et de Flyway, puis à chaque action destructive
du scénario. Le profil n'est pas déployable et son répertoire `.twincare-demo/`
est ignoré.

## Identité, tenant et capacités

Les routes professionnelles exigent un JWT valide. L'organisation active, le
rôle et les capacités cliniques sont dérivés des appartenances serveur. Toute
opération sensible réautorise l'appartenance et l'objet dans le cas d'usage ; une
sélection de tenant côté client n'a aucune autorité.

L'administration du tenant n'accorde pas d'accès clinique. Les médecins et
assistants actifs disposent uniquement des capacités explicites nécessaires.
Confirmation de préparation, audit et clôture restent réservés au médecin.
Acteurs et personnes affectées sont résolus dans l'organisation ; une identité
fournie par le client est ignorée ou rejetée.

La chaîne Spring Security normale repose sur une liste positive méthode + chemin
et se termine par `denyAll`. Les contrôleurs historiques de comptes, utilisateurs,
dossiers, documents, validation et chatbot sont derrière le profil inactif
`legacy-prototype`. La documentation interactive et les routes inconnues restent
fermées pour tous les rôles, y compris administrateur.

## Invitation et session patient

Une invitation est aléatoire, à usage unique, limitée dans le temps et bornée à
un cas. Son secret n'est conservé que sous forme de condensat HMAC avec séparation
de domaine. L'échange verrouille le cas et l'invitation, consomme le justificatif
et émet atomiquement une session courte.

La session patient utilise un cookie `HttpOnly`, `SameSite` et `Secure` sous
HTTPS. Elle possède une chaîne d'authentification dédiée sans JWT. Le serveur
déduit organisation, cas et portée ; chaque écriture reprend les verrous et
revérifie session, invitation, expiration et état du cas. Les mutations exigent
un jeton CSRF lié à la session.

Le lien d'invitation est remis au navigateur dans un fragment d'URL, jamais dans
la requête serveur initiale. Il reste volatil, n'est ni journalisé ni persistant
et n'est copié que par action explicite. Le frontend ne rejoue pas automatiquement
une création ou une émission dont le résultat est ambigu.

La reprise du motif patient renvoie uniquement la valeur exacte utile au
formulaire. Elle est `no-store`, ne crée pas d'audit de lecture et ne persiste pas
le texte dans le stockage du navigateur.

## Données cliniques et concurrence

Le navigateur ne fournit ni provenance, ni certitude, ni acteur. Ces valeurs
sont imposées par le cas d'usage. Les faits atomiques conservent les corrections :
un nouveau fait devient courant et l'ancien est marqué remplacé. Les contraintes
de persistance interdisent deux valeurs courantes concurrentes du même type.

L'enregistrement du motif, sa représentation atomique et l'événement d'audit
partagent une transaction. Les rejeux identiques sont sans effet. Une erreur de
persistance ou d'audit annule toute la mutation.

La préparation professionnelle est calculée par une politique déterministe et
versionnée. La confirmation médicale porte sur la révision exacte relue. Les
lectures revérifient la preuve ; toute modification ultérieure l'invalide. Les
vues composées utilisent un instantané cohérent afin de ne jamais combiner des
révisions différentes.

La clôture exige un médecin, la révision attendue et la preuve de préparation
exacte. Elle verrouille cas, invitations et sessions dans un ordre stable,
révoque les accès patient et enregistre la clôture immuable dans une seule
transaction.

## Documents privés

Il n'existe aucune URL publique vers les octets. Un dépôt valide taille,
extension, type MIME autorisé et signature binaire avant promotion. Le nom
d'origine est nettoyé et utilisé seulement comme métadonnée d'affichage ; la clé
opaque générée côté serveur reste interne.

Le stockage refuse traversée de chemin et évasion par lien symbolique. Il met les
octets en quarantaine et coordonne promotion, remplacement et suppression avec
commit ou rollback. Au démarrage, la réconciliation purge les temporaires sûrs
et échoue fermée si une métadonnée active référence des octets manquants ou si
une réparation ne peut être prouvée. Les fichiers multipart temporaires résident
dans un répertoire privé distinct.

Pour un téléchargement professionnel, le service :

1. autorise l'appartenance et la capacité dans l'organisation ;
2. verrouille le cas puis la métadonnée de pièce jointe ;
3. ouvre une seule fois l'objet final sans suivre les liens symboliques ;
4. vérifie la taille réelle et le SHA-256 sur ce handle ;
5. termine la transaction puis diffuse ce même handle.

La réponse force téléchargement, type autorisé, longueur exacte,
`X-Content-Type-Options: nosniff`, `Cache-Control: no-store` et
`Pragma: no-cache`. V0.1 n'expose ni aperçu public, ni range, ni ETag.

## Audit sans PHI

« Sans PHI » désigne ici l'exclusion du contenu clinique et des identifiants
directs, pas une anonymisation. Les liens techniques vers les cas ou acteurs
peuvent demeurer des données personnelles ; l'audit doit donc lui-même être
protégé, minimisé et soumis à une durée de conservation motivée.

La chronologie métier append-only est distincte des logs applicatifs. Son schéma
fermé autorise seulement : type d'événement, version, instant serveur, catégorie
d'acteur, organisation/cas, révision persistée et éventuel identifiant technique
de sujet.

Elle exclut référence patient, texte clinique, libellé de tâche, nom ou clé de
fichier, empreinte de document, secret, adresse IP, User-Agent et JSON libre. Une
mutation conséquente et son append partagent la transaction. Rejeu idempotent et
no-op ne dupliquent pas la preuve.

La lecture est réservée au médecin, limitée, paginée par curseur et `no-store`.
Les cas antérieurs au mécanisme signalent un historique incomplet ; aucun
backfill ne fabrique une chronologie. Il n'existe pas d'API de suppression avant
validation formelle des règles de rétention, sauvegarde, restauration,
effacement et gel légal.

Les logs opérationnels peuvent contenir un code d'événement, un résultat, une
durée et un identifiant de corrélation opaque. Ils excluent corps de requête,
raison de consultation, document, export, token, email, nom et identifiant lié à
une personne.

## Navigateur et réponses

Toutes les réponses de préparation, y compris les erreurs, sont privées et
`no-store`. CORS utilise une liste explicite. Les écrans ne persistent ni JWT ni
contenu clinique au-delà du strict nécessaire : le JWT professionnel reste dans
`sessionStorage`, le contexte clinique en mémoire et les URL objet sont révoquées.
Les requêtes périmées sont annulées ou ignorées pour éviter qu'une réponse lente
remplace le tenant ou le cas courant.

La démonstration applique une CSP same-origin, ne charge aucun script, police,
mesure d'audience ou service distant et porte un avertissement visible « données
fictives uniquement ».

## Limites avant données réelles

La démo n'est pas homologuée pour des données réelles : sa base H2 et ses exports
ne sont pas chiffrés par l'application. Un usage réel exige notamment TLS,
chiffrement des volumes et sauvegardes, gestion des clés, exploitation de
l'identité et validation des procédures de rétention/restauration. Ces exigences
ne sont pas satisfaites par la seule écoute sur l'interface locale.

### Exigences de la cible — non encore satisfaites

- **Dépôt générique et identité.** Séparer l'entrée sans rattachement de la
  session du cas actuel. Le code SMS prouve le contrôle du téléphone, pas
  l'identité ; limiter tentatives, durée et reprises, sans révéler l'existence
  d'un patient. Traiter homonymes et contacts partagés. Aucun accès au dossier
  antérieur avant rapprochement humain et autorisation ; doute = blocage.
- **Droits de l'équipe.** Établir les missions réelles et les droits minimaux,
  y compris pour l'assistante. Les capacités du prototype ne prouvent pas une
  habilitation juridique. Ne pas imposer une seconde validation clinique à une
  personne dont le rôle ne l'autorise pas.
- **Authentification et exploitation.** Comptes individuels, stratégie
  d'authentification forte adaptée au risque, révocation, TLS, mises à jour,
  sauvegarde et clés gérées. Examiner le risque XSS du JWT professionnel en
  `sessionStorage` ; ne pas le présenter comme inaccessible au JavaScript.
- **Sas et chaîne HDS.** Vérifier les activités et prestataires réellement
  couverts, lieux de traitement et sous-traitants. Documenter l'exploitation
  pour le compte des médecins, y compris locale ; ne pas réduire HDS au serveur.
  Ne pas acheter ou déployer un service sur la seule base de cette documentation.
- **Pièces et traitements.** La quarantaine transactionnelle et les signatures
  de fichiers existantes ne sont pas une analyse antivirus. Avant traitement
  automatique, définir antimalware, isolement OCR, limites de ressources et
  traitement des fichiers suspects. Un contenu de document n'est jamais une
  instruction exécutable ou une autorité pour le modèle.
- **Flux externes.** Aucun contenu patient dans l'IA grand public, une dictée
  distante non maîtrisée, les outils de mesure d'audience ou les espaces génériques
  de partage. Un prestataire SMS reçoit uniquement les éléments nécessaires au
  message neutre ; contrat, rétention et traces sont à vérifier.
- **Réception durable et rétention.** Un accusé distant nécessite une écriture
  locale durable et sa sauvegarde vérifiée. Tester interruption, doublon,
  saturation, perte du poste, restauration et expiration sans remise. Pas de
  purge sur simple téléchargement ; pas de conservation indéfinie en cas de panne.
- **Versement.** Avant purge locale, vérifier que les éléments et sources utiles
  ont rejoint le bon dossier métier. Ni copie, ni confirmation de préparation,
  ni clôture actuelle ne suffisent à prouver ce versement.
- **Politique de données.** Les durées proposées dans le concept sont à approuver,
  avec sauvegardes, gels motivés, droits et procédure d'incident. Les futurs
  effacements doivent être testés et répercutés lors d'une restauration.
- **Cadre réglementaire.** Responsabilités, bases, information, AIPD et qualification
  des fonctions au titre des dispositifs médicaux et de l'IA : preuves requises
  dans la feuille de route. Human-in-the-loop, petite cohorte et pseudonymisation
  ne justifient pas de supprimer ces examens.

Ces obligations sont des critères de livraison à instruire avec l'appui
compétent, pas une certification de TwinCare. Les références publiques sont
rassemblées au plus près des décisions dans le [concept](CONCEPT_OPERATIONNEL.md).

## Vérifications avant livraison

1. Exécuter tests, migrations, builds, lint et contrôles de types disponibles.
2. Inspecter le diff et l'inventaire des fichiers suivis et non suivis.
3. Rechercher secrets, `.env`, uploads, documents et traces de données
   identifiantes ; examiner chaque correspondance.
4. Vérifier isolation inter-organisations, capacités, transitions invalides,
   idempotence, absence de cache et fermeture des routes non autorisées.
5. Vérifier les permissions et chemins de stockage, ainsi que l'inaccessibilité
   de `/demo/**` hors profil `demo`.
6. N'utiliser que des fixtures explicitement synthétiques.
