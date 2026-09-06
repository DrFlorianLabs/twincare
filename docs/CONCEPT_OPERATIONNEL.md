# TwinCare — concept opérationnel

Référence produit au 6 septembre 2026. Ce document décrit **la cible à construire**,
pas une application déjà prête pour des patients. L'[état du code](../README.md)
et la [feuille de route](ROADMAP.md) distinguent le disponible du prévu.

## La décision en une minute

Préparer une consultation programmée avec un récit patient et quelques documents,
puis présenter au médecin une fiche courte dont chaque information peut être
retrouvée dans sa source. Le logiciel métier reste le dossier de référence.

Première équipe proposée : deux médecins et une assistante habilitée. Premier
terrain : adultes avec des documents récents à revoir lors d'une consultation
déjà organisée. Les urgences, mineurs et représentations complexes ne font pas
partie du pilote. Refuser TwinCare ne change ni le rendez-vous ni l'accès au soin.

On conserve le socle existant : cas de préparation, accès bornés, documents
privés, faits avec provenance, tâches et confirmation humaine. On ne reconstruit
pas le projet à zéro. MedFlow/Runtime reste au besoin un nom de moteur interne,
pas un deuxième produit à développer.

| Décision | Choix de départ |
| --- | --- |
| Contacter avant la consultation | Tester un lien externe fixe dans les consignes Doctolib après réservation ; invitation individuelle manuelle en secours. |
| Accéder aux informations Doctolib | Pas de connexion implicite : déclaration patient et rapprochement humain ; récupération ciblée de pièces par un professionnel habilité si nécessaire. |
| Collecter | Navigateur mobile, texte guidé et pièces ; ni application à installer ni chatbot ouvert. |
| Préparer | Extraction documentaire puis brouillon sourcé, lorsque leur qualité est démontrée ; restitution déterministe toujours disponible. |
| Stocker | Sas temporaire hébergé en France dans un périmètre HDS vérifié ; traitement et zone de travail sur une application locale exploitée au cabinet. |
| Terminer | Le médecin vérifie, corrige et verse les éléments utiles au bon dossier métier ; ce versement doit être confirmé. |

## 1. Contacter le patient sans API Doctolib

### Chemin recommandé à tester

L'aide officielle décrit, pour les offres payantes, une consigne après réservation
contenant une URL externe, paramétrable par agenda et motif, visible depuis le
compte patient et les e-mails liés au rendez-vous. C'est un point d'entrée,
**pas un échange de données avec TwinCare**.
[Source : aide Doctolib sur les consignes](https://doctolib.zendesk.com/hc/fr/articles/360054000752-Transmettre-des-consignes-%C3%A0-mes-patients-avant-et-ou-apr%C3%A8s-la-prise-de-rendez-vous).

Sur le compte du cabinet : administrateur → Gestion des rendez-vous → Consignes
→ agendas et motifs concernés → après réservation → rédiger une consigne → lien.
Tester réellement l'offre, les droits, la réservation en ligne, les rendez-vous
déjà pris et ceux créés au secrétariat ; ne pas présumer une diffusion identique.

Texte proposé pour ce test :

> Vous pouvez préparer votre consultation en racontant ce que vous souhaitez
> aborder et en ajoutant vos documents : « Préparer ma consultation ».
> C'est facultatif ; votre rendez-vous reste inchangé.

L'URL est fixe et ne contient aucune identité, date de naissance, référence de
dossier ou autorisation d'accès. TwinCare ne reçoit donc ni patient identifié,
ni agenda, ni annulation. Sans liste fiable des personnes invitées, il ne sait
pas qui n'a pas répondu : aucune relance automatique ciblée n'est prévue.

### Les autres chemins, avec leurs limites

| Option | Usage retenu / condition |
| --- | --- |
| Invitation individuelle | L'équipe crée un cas, vérifie le destinataire et remet le lien par un canal validé. Le code génère ce lien aujourd'hui, mais n'envoie pas de SMS. Tout futur SMS est neutre, sans motif ; le temps d'envoi est compté. |
| Documents déjà déposés dans Doctolib | Éviter un second dépôt patient. Un professionnel peut récupérer les pièces accessibles selon ses droits et les importer dans un futur parcours ciblé ; ce parcours professionnel n'est pas encore livré. |
| Export officiel | Examiner le contenu, les droits, le coût et la fraîcheur avant tout développement. Ne pas en déduire un agenda vivant ou un suivi des annulations. |
| Lecture d'écran / OCR / extension | Hors chemin critique : interface fragile, risque d'erreur de patient et droit contractuel à établir. Lire le DOM plutôt que l'image ne supprime pas ces contraintes. |
| Automatisation de clics et identifiants partagés | Non retenue. Aucun contournement des accès ou partage du compte du praticien. |

Les [fonctions natives de préparation documentaire](https://doctolib.zendesk.com/hc/fr/articles/360054532691-Consulter-les-documents-partag%C3%A9s-par-mon-patient-avant-son-rendez-vous)
servent de comparateur. L'[export officiel](https://doctolib.zendesk.com/hc/fr/articles/33288343986196-Tout-savoir-sur-l-export-de-mes-donn%C3%A9es)
et les [possibilités de personnalisation des SMS](https://doctolib.zendesk.com/hc/fr/articles/360058834452-Personnaliser-le-SMS-de-rappel-de-rendez-vous)
ne sont pas assimilés à une API d'intégration.

## 2. Le parcours patient cible

Quatre écrans courts, avec retour en arrière et aide humaine possible.

1. **Entrer.** Expliquer la finalité, les destinataires et le caractère facultatif.
   Durée visée : 3 à 5 minutes. Afficher que le formulaire n'est pas surveillé en
   continu et ne remplace pas les voies d'urgence habituelles (15 / 112 en France).
2. **Déclarer le rendez-vous et raconter.** Nom de naissance, prénom, date de
   naissance, téléphone, praticien et date déclarée, sous réserve de minimisation
   validée. Le code SMS vérifie le téléphone, pas l'identité médicale. Le patient
   raconte ce qu'il souhaite aborder ; les repères de récit sont facultatifs,
   non diagnostiques et relus par le référent médical. « Je ne sais pas » est
   possible ; aucune saisie exhaustive des antécédents n'est imposée.
3. **Ajouter les pièces utiles.** PDF ou photos ; ne pas recopier les médicaments
   ou les résultats biologiques. La proposition de cinq pièces est un plafond à
   éprouver, pas une capacité actuelle : le code accepte aujourd'hui une seule
   pièce de 10 Mio. Prévoir lisibilité, erreur et reprise sans double dépôt.
4. **Relire et transmettre.** Montrer le récit exact et la liste des pièces.
   Accusé : « Dépôt reçu, à rattacher. Sa lecture avant la consultation n'est pas
   garantie. » Aucun compte longitudinal ni conversation ouverte après envoi.

Le dépôt générique reste séparé de tout dossier préexistant. L'équipe habilitée
le rapproche du patient et du rendez-vous dans les outils habituels ; homonymie,
date erronée ou doute bloquent le rattachement. Aucun dossier ancien n'est montré
au déclarant. Une annulation ou un changement de date est traité manuellement.
Une aide à la saisie doit distinguer la personne concernée de l'auteur effectif.

**Voix : après preuve du besoin.** Un bouton volontaire pourra produire une
transcription maîtrisée, relue par le patient, puis effacer l'audio. Pas d'analyse
des émotions ou de diagnostic vocal. La dictée du clavier peut recourir à un
service distant : ne pas la présenter comme une fonction locale garantie.

## 3. Préparer automatiquement sans inventer

La première automatisation utile est de retrouver les informations, pas de
prendre une décision clinique. Chaque étape doit pouvoir échouer sans rendre
les sources indisponibles au soignant autorisé.

| Étape cible | Résultat attendu | Échec visible |
| --- | --- | --- |
| Réception | Format et taille valides, analyse antimalware, doublons exacts repérés. | Pièce suspecte isolée ; contrôle de signature actuel ≠ antivirus. |
| Extraction | Texte natif ou OCR des scans, relié au document et à la page. | « Non extrait / illisible », jamais un texte deviné. |
| Structuration | Éléments avec passage justificatif, origine, date du document et date de réception ; version du traitement. | Date absente laissée inconnue ; origine ou correspondance non démontrée bloque l'élément. |
| Brouillon | Rubriques courtes : demande, éléments documentés, déclarations, points à vérifier, sources et limites. | Retour au récit et aux pièces si le modèle ou ses contrôles échouent. |
| Relecture | Le médecin corrige et retient les éléments utiles ; les versions et sources sont conservées. | Pas de confirmation automatique, pas d'envoi ou d'écriture clinique par le modèle. |

Une pièce est une donnée non fiable, jamais une instruction pour le logiciel.
Un éventuel modèle local n'a aucun outil d'action. Ses sorties passent un contrat
structuré et des contrôles de référence ; ceux-ci ne prouvent pas à eux seuls
la justesse clinique. Versionner modèle, consigne et schéma ; tester les erreurs
de négation, temporalité, posologie, attribution de patient et omission.

Ne jamais transformer « non renseigné » en « absent », ni considérer la dernière
ordonnance trouvée comme la preuve du traitement effectivement pris. Des
contradictions repérées sont des points à vérifier, sans promettre de tout détecter.
La qualification réglementaire de cette fonction précède son usage réel.

Exemple **entièrement fictif**, illustrant la cible et non une sortie actuelle :

| Élément de la fiche | Contenu avec source |
| --- | --- |
| Demande | « Je ne sais plus quels médicaments reprendre. » — récit patient, 6 septembre. |
| Événement documenté | Sortie d'hospitalisation le 3 septembre — lettre de sortie, page 1. |
| Déclaration distincte | Le patient dit avoir repris son ancienne ordonnance — récit patient. |
| À vérifier par le médecin | Listes différentes entre deux pièces ; traitement effectivement pris à confirmer. Aucun arbitrage thérapeutique automatique. |

L'assistante vérifie le rapprochement et les pièces dans la limite de ses
missions. Une seconde lecture clinique systématique ne lui est pas imposée.
Le médecin vérifie ensuite l'identité du dossier métier ouvert, copie les
éléments retenus et verse les sources nécessaires. Il confirme ce versement :
**« copié » n'est ni « rattaché au bon patient » ni « conservé au dossier ».**
La clôture actuelle de TwinCare ne prouve pas ce versement ; un mécanisme dédié
reste à construire.

## 4. Où vivent les données

Le choix local-first signifie traitement documentaire au cabinet, et non absence
de toute infrastructure distante. Le patient doit pouvoir déposer lorsque le
cabinet est fermé. La cible sépare quatre responsabilités.

| Zone cible | Contenu et usage | Exploitation à prévoir |
| --- | --- | --- |
| Sas HDS en France | Identité déclarée, récit et pièces temporaires ; aucun dossier longitudinal consultable. | Service et sous-traitants couverts, chiffrement, supervision, support et anti-abus. |
| Application locale dédiée | Dépôts reçus, rapprochement, sources, OCR et brouillons ; accès par rôles. | Poste/serveur géré, volumes chiffrés, comptes individuels, mises à jour et clés. |
| Logiciel métier | Informations validées et pièces utiles au soin. | Professionnels responsables du bon versement et de leur dossier. |
| Sauvegarde séparée | Copie chiffrée restaurable des données de travail nécessaires. | Responsable désigné, périmètre contractuel, tests de restauration et rotation. |

L'application locale récupère les dépôts par connexion sortante authentifiée,
sans exposer directement le serveur du cabinet sur Internet. Elle accuse
réception après écriture durable **et sauvegarde vérifiée du dépôt**, pas après
un simple téléchargement HTTP. Un transfert rejoué ne duplique pas les données.
La purge distante dépend de cet accusé, avec traitement explicite des incidents.

Le choix HDS concerne le service réellement rendu, pas seulement le nom de
l'hébergeur : exploitation, administration et sauvegarde peuvent également entrer
dans le périmètre. L'organisation juridique de la MSP et l'exploitation pour le
compte des médecins doivent être examinées ; « local » n'est pas une exemption
automatique. [Référence : ANS, certification HDS](https://esante.gouv.fr/produits-services/hds).

### Conservation : hypothèses, pas délais légaux

- Sas : suppression visée sous 24 heures après réception durable confirmée ;
  plafond proposé de 7 jours après dépôt, avec alerte avant échéance. Sans
  transfert possible, appliquer la procédure d'incident et informer le patient
  de la non-transmission ; ne pas supprimer silencieusement.
- Dépôt non rattaché : examen sous 7 jours, puis résolution de l'anomalie ou
  suppression selon la procédure approuvée, sans association hasardeuse.
- Zone de travail : purge proposée 30 jours après versement vérifié des éléments
  et sources utiles au dossier métier. Une copie de résumé seule ne suffit pas.
- Audio futur : suppression après validation de la transcription, avec délai
  court défini pour les abandons. Audit et sauvegardes : politiques distinctes,
  durées motivées et restauration n'annulant pas silencieusement les suppressions.

Ces durées ne sont ni implémentées ni juridiquement validées. Elles ne remplacent
pas la conservation du dossier médical. Un dispositif de gel motivé et la gestion
des demandes d'exercice de droits sont à définir avant tout effacement réel.

Chiffrer les coûts sur devis : sas, SMS, matériel, maintenance, sauvegardes,
restauration, sécurité et appui réglementaire, plus temps humain par consultation.
Si l'équipe ne peut pas exploiter le local, décider explicitement d'un service
HDS unifié ; ne pas maintenir deux déploiements ni prétendre que le local est gratuit.

## 5. Conditions avant un pilote réel

Ce cadrage n'est ni une homologation ni un avis juridique. Les responsables du
projet doivent obtenir une revue compétente et des preuves sur les points suivants.

| Point | Décision / preuve à obtenir |
| --- | --- |
| Responsabilités RGPD | Définir qui décide des finalités (médecins, structure, éventuelle responsabilité conjointe), qui sous-traite, et les contrats associés. Qualifier les bases des articles 6 et 9 pour chaque finalité. |
| Information et participation | Notice compréhensible, droits et contact ; distinguer participation volontaire et base juridique du traitement. Une case « consentement » ne règle pas tout. Aucune réutilisation pour entraîner un modèle par défaut. |
| Analyse d'impact | Évaluer l'obligation d'AIPD et documenter l'analyse avant le réel ; la réaliser si requise, avec risques résiduels et mesures. Le petit nombre de testeurs ne suffit pas à conclure à une exemption. |
| Hébergement et exploitation | Vérifier certificats, activités couvertes, lieux de traitement, sous-traitants, administration et sauvegardes ; documenter l'exploitation locale. |
| Dispositif médical et IA | Formaliser la destination revendiquée et qualifier les fonctions réellement fournies, notamment la synthèse documentaire. Une relecture humaine ou l'étiquette « administratif » n'exclut pas à elle seule le statut de dispositif médical. Examiner aussi les obligations du règlement IA applicables au rôle et au calendrier du projet. |
| Identité et sécurité clinique | Protocole de rapprochement, habilitations, échec sans fuite, sources vérifiables, incidents et retour au soin habituel. |
| Exploitation | Responsable, budget, support, restauration prouvée, arrêt du pilote, conservation et confirmation de versement. |

Les principes d'accès limité, d'information, de registre et de sous-traitance sont
rappelés par la [CNIL pour les professionnels de santé](https://www.cnil.fr/fr/rgpd-et-professionnels-de-sante-liberaux-ce-que-vous-devez-savoir).
Pour les évaluations à conduire : [AIPD — CNIL](https://www.cnil.fr/fr/RGPD-analyse-impact-protection-des-donnees-aipd),
[IA générative — CNIL](https://www.cnil.fr/fr/les-questions-reponses-de-la-cnil-sur-lutilisation-dun-systeme-dia-generative),
[qualification des logiciels — MDCG 2019-11](https://health.ec.europa.eu/document/download/b45335c5-1679-4c71-a91c-fc7a4d37f12b_en?filename=mdcg_2019_11_en.pdf)
et [cadre européen du règlement IA](https://digital-strategy.ec.europa.eu/en/policies/regulatory-framework-ai).
Un identifiant opaque ne rend pas les données anonymes : voir la
[distinction anonymisation / pseudonymisation de la CNIL](https://www.cnil.fr/fr/technologies/lanonymisation-de-donnees-personnelles).

## 6. La preuve qui justifie la suite

Tester d'abord des cas fictifs et comparer au parcours habituel, y compris les
fonctions natives de Doctolib. Mesurer toute la chaîne : invitation, aide patient,
rapprochement, recherche, correction, lecture et versement. Le temps gagné chez
le médecin ne doit pas être absorbé par un surcroît de travail ailleurs.

Objectifs proposés, non démontrés : médiane d'au moins 60 secondes nettes gagnées
par consultation éligible, gain total d'équipe positif, durée patient médiane
inférieure ou égale à 5 minutes, aucune erreur critique observée. Inclure les
non-répondants et les échecs ; ne pas calculer uniquement sur les fiches réussies.
L'absence d'erreur dans un petit pilote n'est pas une preuve générale de sécurité.

La [feuille de route](ROADMAP.md) transforme ce concept en lots testables. Avant
cette preuve, pas de suivi chronique, coordination aval, portail généraliste,
santé environnementale, diagnostic, prescription ou plateforme d'agents.
