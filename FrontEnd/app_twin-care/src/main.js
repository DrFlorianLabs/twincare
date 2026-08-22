import { createApp } from 'vue'

import App from './App.vue'
import { captureInvitationSecret } from './security/invitationSecret.js'
import './styles/main.css'

const invitationCapture = captureInvitationSecret(window.location, window.history)

createApp(App, { invitationCapture }).mount('#app')
