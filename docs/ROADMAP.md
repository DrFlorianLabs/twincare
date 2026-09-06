# TwinCare — feuille de route de réalisation

État au 6 septembre 2026. Périmètre : [concept opérationnel](CONCEPT_OPERATIONNEL.md).
Ce document est le backlog de référence, pas une promesse de dates ou de gains.

## 0. Point de départ : conserver le socle

La [PR #50](https://github.com/DrFlorianLabs/twincare/pull/50) est intégrée sur
`main` : vignette du motif avec source, date, historique et revue ; copie texte
avec tâches et liste de pièces ; export JSON ; contrôle de révision et audit de
relecture. Les tests et builds de cet incrément ont réussi dans la
[CI de la branche](https://github.com/DrFlorianLabs/twincare/actions/runs/34016649618).
Cela ne vaut pas validation visuelle ou autorisation d'usage réel.

Les fondations réutilisables sont le cas de préparation, les invitations et
sessions bornées, le stockage privé, les faits versionnés, les capacités par
organisation, les tâches, la confirmation et la clôture. Un document reçu n'est
pas analysé ; un fait « motif » n'est pas un dossier longitudinal.

## 1. Lots ordonnés et critères de sortie

Les responsabilités ci-dessous sont des rôles à attribuer, pas des personnes
déjà engagées. Aucun lot n'autorise l'usage de données réelles avant le lot 5.

| Lot / statut | Livrable borné | Critère de sortie / responsable |
| --- | --- | --- |
| **1 — À faire : essai de bout en bout fictif** | Faire utiliser la démo puis l'espace professionnel Vue à l'équipe ; tester la disponibilité du lien natif Doctolib sur le compte réel sans exposer de donnée patient ; comparer au fonctionnement habituel. | Compte rendu daté : écrans réellement essayés, étapes inutiles, source accessible et temps de chaque rôle. Référent médical + assistante habilitée ; développeur pour l'installation. |
| **2 — À construire : dépôt générique et identité** | Entrée distincte de l'invitation individuelle ; identité déclarée, téléphone vérifié, récit et pièce, reçu non rattaché, file de rapprochement humain. | Tests d'homonymie, téléphone partagé, erreur de date, accès hors organisation, doubles envois et session expirée. Aucun dossier existant visible au déclarant ; aucun rattachement automatique. Développement + référent identité. |
| **3 — À construire / contractualiser : exploitation** | Sas HDS + récupération sortante vers l'application locale, chiffrement et clés, sauvegarde, reçu durable, incidents, conservation et support. | Restaurer réellement un dépôt fictif après panne ; prouver l'absence de purge distante prématurée et les droits minimaux. Exploitant + appui sécurité/protection des données. |
| **4 — À éprouver : préparation documentaire** | Une source documentaire prioritaire, extraction/OCR, passage justificatif et affichage côte à côte. Augmenter le nombre de pièces seulement si nécessaire. Brouillon par modèle local uniquement après comparaison au déterministe. | Corpus exclusivement synthétique, erreurs revues par le médecin, références retrouvables, repli sans modèle, mesure du temps de correction et coût matériel. Référent médical + développement. |
| **5 — Bloqué avant preuves : pilote réel limité** | Parcours approuvé, responsabilités signées, qualification réglementaire, notice et procédures, bons droits, dispositif de mesure. | Tous les verrous ci-dessous levés ; décision explicite des responsables. Démarrage avec deux médecins et une assistante selon ses droits, sans élargissement automatique. |
| **Après preuve d'usage — Non engagé** | Voix maîtrisée, intégration officielle éventuelle, autres types de faits ou autres usages. | Une demande démontrée, un gain et un arbitrage de périmètre pour chaque extension. Aucun de ces éléments n'est un prérequis du premier pilote. |

Les travaux documentaires de conformité peuvent avancer pendant les essais
synthétiques. Une extraction non fiable n'empêche pas de tester le récit et les
pièces sans modèle ; un pilote ainsi réduit doit être décrit comme tel. L'essai
manuel du lot 1 reste à faire, même si les tests automatisés sont verts.

## 2. Le prochain travail concret

Avant d'ajouter une fonctionnalité :

1. Rejouer le scénario de la démo sur Mac, puis un cas fictif dans l'espace Vue
   professionnel : invitation → récit corrigé → pièce → tâche → revue → copie →
   confirmation → clôture. La démo intégrée seule ne couvre pas tout ce parcours.
2. Essayer les échecs : motif rejeté, réponse périmée après changement de patient,
   invitation réutilisée, rôle insuffisant et pièce invalide. Noter les messages
   réellement affichés ; ne pas confondre tests unitaires et essai navigateur.
3. Vérifier dans le compte Doctolib l'offre et les droits ; tester la consigne
   après réservation selon les modes de prise de rendez-vous. Aucun changement
   sur le compte ni envoi à un patient n'a été réalisé par la mise à jour du dépôt.
4. Choisir la première pièce utile et observer comment elle arrive aujourd'hui.
   Si le natif suffit, supprimer l'étape TwinCare redondante.
5. Consigner un résultat court : conserver / supprimer / corriger ; temps par
   rôle ; prochain incrément unique. Puis spécifier le dépôt non rattaché du lot 2.

Pour ce lot 2, définir d'abord les états et droits. Ne pas réutiliser un
`PreparationCase` déjà rattaché comme boîte de réception publique. Une réponse
générique doit rester sans effet sur les dossiers existants jusqu'à la décision
humaine tracée. Les états proposés dans l'architecture ne sont pas des API livrées.

## 3. Verrous avant le premier patient réel

Tous sont **non levés dans cette feuille de route** tant qu'une preuve datée et
son responsable ne sont pas référencés. Un fichier de cadrage n'est pas la preuve
d'une mise en œuvre.

- [ ] Responsable(s) de traitement et sous-traitants identifiés ; finalités,
      bases juridiques, registre et contrats établis.
- [ ] Nécessité de l'AIPD évaluée et motivée ; analyse réalisée si requise ; risques
      résiduels examinés avec l'appui compétent.
- [ ] Destination du logiciel et qualification dispositif médical documentées ;
      obligations applicables à l'IA analysées pour les fonctions effectivement utilisées.
- [ ] Notice patient, refus sans effet sur les soins, aide et exercice des droits
      opérationnels ; aucune réutilisation d'entraînement par défaut.
- [ ] Chaîne HDS et lieux de traitement vérifiés, y compris les opérations
      externalisées ; exploitation locale, clés, support et budget attribués.
- [ ] Protocole de rapprochement et droits testés ; aucune exposition d'un dossier
      sur identité déclarée ; contacts partagés et incidents pris en charge.
- [ ] Contrôles des pièces, TLS, anti-abus, authentification professionnelle,
      révocation et absence de fuite dans les traces vérifiés.
- [ ] Sauvegarde restaurée en essai, accusé de réception durable testé, purge et
      gestion d'incident validées ; durées et gels documentés.
- [ ] Fiche et sources vérifiables ; médecin responsable du contenu retenu ;
      vérification du bon dossier et confirmation de versement opérationnelles.
- [ ] Périmètre, méthode de mesure, critères d'arrêt et responsables du pilote
      validés. La prise en charge habituelle fonctionne en cas de refus ou de panne.

Références et limites juridiques : [concept opérationnel](CONCEPT_OPERATIONNEL.md).
Exigences techniques : [socle de sécurité](SECURITY_BASELINE.md).

## 4. Mesurer le travail réellement économisé

Pour chaque consultation éligible du pilote, noter des durées sans contenu
clinique dans le tableau de mesure : invitation, aide à la saisie, rapprochement,
recherche de pièces, lecture/correction médicale et versement. Distinguer temps
patient, temps médecin, temps assistante et travail d'exploitation partagé.

Comparer à une référence explicitée : mêmes tâches sur cas fictifs comparables
avant le réel, puis observations comparables selon un protocole fixé avant le
pilote. Ne pas présenter ce petit essai d'usage comme une étude d'efficacité clinique.

Calcul proposé : gain net par consultation = temps total d'équipe de référence
moins temps total d'équipe avec TwinCare. Rapporter séparément les temps par rôle
et les coûts fixes ; afficher l'effectif, les abandons et les données manquantes.

| Indicateur | Seuil de décision proposé, à valider avant pilote |
| --- | --- |
| Gain net par consultation éligible | Médiane ≥ 60 secondes et somme des gains d'équipe positive. Inclure invitations infructueuses, aide et corrections ; aucune exclusion silencieuse des non-répondants. |
| Effort patient | Médiane ≤ 5 minutes ; décrire les abandons et besoins d'assistance, pas uniquement les parcours terminés. |
| Qualité et sécurité | Aucune erreur critique observée. Tracer les omissions, erreurs de source et corrections ; l'absence d'événement ne prouve pas une sécurité générale. |
| Charge d'exploitation | Temps de support, incidents, coûts fixes et variables chiffrés ; budget acceptable décidé par l'équipe, pas supposé. |

Le dénominateur « consultations éligibles » doit être tenu par l'équipe lors du
pilote, puisque TwinCare ne synchronise pas l'agenda. Une mesure pseudonymisée
reste soumise aux règles de protection des données et ne va pas dans GitHub.

**Arrêt immédiat** en cas de mauvais rattachement, fuite, accès indu, élément
clinique inventé susceptible d'influencer le soin ou perte silencieuse d'un dépôt.
Revenir au parcours habituel, traiter l'incident puis décider explicitement de la
reprise. Sans gain d'équipe ou avec trop de corrections, simplifier avant toute
nouvelle automatisation.

## 5. Discipline de livraison

Un incrément utile, des tests pertinents et des limites explicites. Une évolution
changeant le comportement met à jour le tableau du README et cette feuille de
route ; une évolution de contrat modifie aussi architecture et sécurité.

La publication sur `main` ne vaut ni déploiement, ni achat de prestation, ni
validation réglementaire. Les propositions historiques et les mises à jour de
dépendances sont examinées séparément ; aucune fusion en bloc du dépôt.
