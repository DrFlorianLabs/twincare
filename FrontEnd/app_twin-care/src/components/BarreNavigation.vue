<template>
  <div style="position: fixed; bottom: 20px; left: 50%; transform: translateX(-50%); width: 90%; max-width: 400px; z-index: 1000;">
    <v-bottom-navigation bg-color="#6B7280" color="#ffffff" elevation="6" rounded="pill" height="56">
      <v-btn to="/preparation" style="min-width: 33%; padding: 0;">
        <v-icon size="22" class="mb-1">mdi-forum-outline</v-icon>
        <span class="font-weight-medium" style="font-size: 0.65rem;">Préparation</span>
      </v-btn>

      <v-btn to="/documents" style="min-width: 33%; padding: 0;">
        <v-icon size="22" class="mb-1">mdi-file-upload-outline</v-icon>
        <span class="font-weight-medium" style="font-size: 0.65rem;">Envoyer</span>
      </v-btn>

      <v-btn to="/mes-documents" style="min-width: 33%; padding: 0;">
        <v-icon size="22" class="mb-1">mdi-folder-outline</v-icon>
        <span class="font-weight-medium" style="font-size: 0.65rem;">Documents</span>
      </v-btn>
    </v-bottom-navigation>
  </div>
</template>
