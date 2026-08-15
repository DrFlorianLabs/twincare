<template>
  <v-container fluid class="fill-height pa-0" style="background: linear-gradient(180deg, #cae8fa 0%, #f4f9fd 100%);">
    <v-btn icon="mdi-chevron-left" variant="text" color="#2A93D5" size="large" style="position: absolute; top: 20px; left: 10px;" to="/" />

    <v-row justify="center" align="center" class="w-100 ma-0">
      <v-col cols="10" sm="8" md="6" class="text-center">
        <v-icon size="56" color="#2A93D5" class="mb-5">mdi-lock-reset</v-icon>
        <h1 class="font-weight-black mb-5" style="color: #2A93D5; font-size: 2.2rem; line-height: 1.2;">
          Mot de passe oublié
        </h1>

        <v-alert type="info" variant="tonal" class="text-left rounded-lg mb-6">
          La récupération autonome du compte n'est pas encore activée dans ce prototype. Pour éviter toute prise de contrôle d'un compte, aucun mot de passe ne peut être changé à partir de la seule connaissance d'une adresse mail.
        </v-alert>

        <p class="text-body-2 mb-6" style="color: #546E7A;">
          Contactez l'équipe qui vous a transmis l'accès TwinCare. Une procédure de récupération sécurisée sera ajoutée avant le déploiement patient réel.
        </p>

        <v-btn to="/" color="#2A93D5" block rounded="lg" size="large" class="text-white font-weight-bold text-none">
          Retour à la connexion
        </v-btn>
      </v-col>
    </v-row>
  </v-container>
</template>
