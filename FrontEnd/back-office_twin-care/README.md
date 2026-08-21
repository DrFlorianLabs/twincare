# TwinCare Consultation — interface professionnelle

Interface Vue du parcours professionnel V0.1 en amont de consultation.

## Parcours actif

- connexion professionnelle ;
- découverte serveur des organisations et capacités ;
- file paginée des préparations ;
- vignette avec motif patient non vérifié, état, actions et document optionnel ;
- tâches humaines ;
- confirmation explicite par le médecin de la révision relue.

Les anciens dossiers, documents, utilisateurs et chatbot du prototype ne font
pas partie du parcours actif.

## Configuration locale

`VITE_API_BASE_URL` est optionnel. Sans cette variable, l'API est appelée sur
la même origine. Aucun identifiant d'organisation n'est configuré côté client.

Le jeton professionnel reste dans `sessionStorage`. Aucun contenu clinique
n'est stocké dans le navigateur, journalisé ou envoyé à un service tiers.

## Vérification

```sh
npm ci
npm run test:unit
npm run build
npm audit --omit=dev --audit-level=high
```

Le dépôt n'est pas autorisé pour des données de patients réels.
