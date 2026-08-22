# TwinCare Consultation — interface professionnelle

Interface Vue du parcours professionnel V0.1 en amont de consultation.

## Parcours actif

- connexion professionnelle ;
- découverte serveur des organisations et capacités ;
- création manuelle idempotente d'une préparation et remise explicite du lien patient ;
- file paginée des préparations ;
- vignette avec motif patient non vérifié, état, actions et document optionnel ;
- tâches humaines ;
- confirmation explicite par le médecin de la révision relue.

Les anciens dossiers, documents, utilisateurs et chatbot du prototype ne font
pas partie du parcours actif.

## Configuration locale

`VITE_API_BASE_URL` est optionnel. Sans cette variable, l'API est appelée sur
la même origine. Aucun identifiant d'organisation n'est configuré côté client.

`VITE_PATIENT_PREPARATION_URL` doit contenir l'adresse absolue `https` de
l'interface patient, sans query string, fragment ni identifiants. `http` est
accepté uniquement pour les adresses loopback locales de développement. Le lien
d'invitation est construit uniquement sous la forme
`<adresse>#token=<secret>`. Sans cette configuration, l'émission est désactivée
avant tout appel réseau. Voir `.env.example` pour le développement local.

Le jeton professionnel reste dans `sessionStorage`. Aucun contenu clinique
n'est stocké dans le navigateur, journalisé ou envoyé à un service tiers. Le
secret d'invitation reste uniquement en mémoire jusqu'à la fermeture de la
boîte de dialogue, au changement d'organisation ou à la déconnexion.

## Vérification

```sh
npm ci
npm run test:unit
npm run build
npm audit --omit=dev --audit-level=high
```

Le dépôt n'est pas autorisé pour des données de patients réels.
