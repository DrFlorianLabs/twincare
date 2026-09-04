import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

import 'vuetify/styles'
import { createVuetify } from 'vuetify'
import {
  VAlert, VApp, VAppBar, VAppBarTitle, VBtn, VCard, VCardActions, VCardText,
  VCardTitle, VChip, VCol, VContainer, VDialog, VForm, VIcon, VList, VListItem,
  VListItemSubtitle, VListItemTitle, VMain, VPagination, VRow, VSelect,
  VSkeletonLoader, VSpacer, VTextField
} from 'vuetify/components'
import * as directives from 'vuetify/directives'
import '@mdi/font/css/materialdesignicons.css'

const vuetify = createVuetify({
  components: {
    VAlert, VApp, VAppBar, VAppBarTitle, VBtn, VCard, VCardActions, VCardText,
    VCardTitle, VChip, VCol, VContainer, VDialog, VForm, VIcon, VList, VListItem,
    VListItemSubtitle, VListItemTitle, VMain, VPagination, VRow, VSelect,
    VSkeletonLoader, VSpacer, VTextField
  },
  directives,
  theme: {
    defaultTheme: 'twincare',
    themes: {
      twincare: {
        dark: false,
        colors: {
          primary: '#165C4A',
          secondary: '#42675E',
          surface: '#FFFFFF',
          background: '#F4F7F5',
          error: '#B3261E',
          warning: '#8A5800',
          success: '#2E6B45'
        }
      }
    }
  }
})

createApp(App).use(router).use(vuetify).mount('#app')
