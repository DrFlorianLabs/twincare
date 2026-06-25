<template>
  <div class="environment-page">

    <div ref="mapContainer" class="map-container"></div>

    <div class="top-controls">

      <v-btn icon="mdi-menu" elevation="4" class="mr-3 bg-white control-btn" @click="toggleDrawer"></v-btn>

      <v-card class="status-pill rounded-pill d-flex align-center px-4 py-2 elevation-4 flex-grow-1">
        <div>
          <div class="font-weight-bold status-title">
            Carte {{ modeActif === 'pollen' ? 'du pollen' : 'de l\'air' }}
          </div>
          <div v-if="!isLoading" class="text-caption font-weight-medium" :style="{ color: modeActif === 'pollen' ? envData.pollenColor : envData.airColor, marginTop: '-2px' }">
            {{ modeActif === 'pollen' ? envData.pollenStatus : envData.airStatus }}
          </div>
          <div v-else class="text-caption" style="color: #6B7280; margin-top: -2px;">
            Recherche de position...
          </div>
        </div>
        <v-spacer></v-spacer>
        <v-icon size="28" :color="modeActif === 'pollen' ? envData.pollenColor : envData.airColor">
          {{ modeActif === 'pollen' ? 'mdi-flower-pollen' : 'mdi-cloud-outline' }}
        </v-icon>
      </v-card>

      <v-btn icon elevation="4" class="ml-3 bg-white control-btn" @click="basculerMode">
        <v-icon size="24" color="#37474F">
          {{ modeActif === 'pollen' ? 'mdi-cloud-outline' : 'mdi-flower-pollen' }}
        </v-icon>
      </v-btn>

    </div>

    <div class="location-chip">
      <v-icon size="17" color="#2A93D5" class="mr-1">mdi-crosshairs-gps</v-icon>
      <span>{{ locationLabel }}</span>
      <span class="live-dot"></span>
    </div>

    <div class="map-legend">
      <div class="legend-title">Indice pollen</div>
      <div class="legend-scale">
        <span style="background:#66BB6A"></span>
        <span style="background:#F9A825"></span>
        <span style="background:#E53935"></span>
      </div>
      <div class="legend-labels"><span>Faible</span><span>Élevé</span></div>
    </div>

    <v-card class="environment-sheet rounded-xl elevation-8">
      <div class="d-flex align-center mb-3">
        <div>
          <div class="sheet-kicker">PRÉVISION AUJOURD’HUI</div>
          <div class="sheet-title">Pollens autour de vous</div>
        </div>
        <v-spacer></v-spacer>
        <v-chip :color="envData.pollenColor" variant="flat" size="small" class="text-white font-weight-bold">
          {{ envData.pollenStatus }}
        </v-chip>
      </div>

      <div class="pollen-grid">
        <div v-for="pollen in pollenDetails" :key="pollen.name" class="pollen-item">
          <div class="d-flex align-center justify-space-between">
            <span class="pollen-name">{{ pollen.name }}</span>
            <span class="pollen-level" :style="{ color: pollen.color }">{{ pollen.level }}</span>
          </div>
          <div class="pollen-track">
            <span :style="{ width: pollen.value + '%', background: pollen.color }"></span>
          </div>
        </div>
      </div>

      <div class="air-summary">
        <v-icon size="19" color="#2A93D5" class="mr-2">mdi-weather-windy</v-icon>
        <span>Qualité de l’air</span>
        <v-spacer></v-spacer>
        <strong :style="{ color: envData.airColor }">{{ envData.airStatus }}</strong>
      </div>
    </v-card>

  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, inject, nextTick } from "vue";
import L from "leaflet";
import "leaflet/dist/leaflet.css";

const toggleDrawer = inject('toggleDrawer');
const modeActif = ref('pollen');
const isLoading = ref(true);
const pitchMode = new URLSearchParams(window.location.search).get('pitch') === '1';
const locationLabel = ref('Position en cours…');

const pollenDetails = ref([
  { name: 'Graminées', level: 'Élevé', value: 82, color: '#E67E22' },
  { name: 'Bouleau', level: 'Faible', value: 28, color: '#43A047' },
  { name: 'Ambroisie', level: 'Faible', value: 18, color: '#43A047' }
]);

const envData = ref({
  airStatus: "En attente...",
  airColor: "#9e9e9e",
  pollenStatus: "En attente...",
  pollenColor: "#9e9e9e"
});

const demoEnvData = {
  airStatus: 'Bon',
  airColor: '#2E9D62',
  pollenStatus: 'Risque modéré',
  pollenColor: '#E67E22'
};

const mapContainer = ref(null); 
let map = null;
let currentLayer = null;
let positionMarker = null;

let userLat = 43.60548;
let userLon = 2.24167;

function demanderGeolocalisation() {
  isLoading.value = true;

  if (pitchMode) {
    locationLabel.value = 'Castres · Position localisée';
    envData.value = demoEnvData;
    initialiserCarte(userLat, userLon, true);
    return;
  }

  if (navigator.geolocation) {
    navigator.geolocation.getCurrentPosition(
      (position) => {
        userLat = position.coords.latitude;
        userLon = position.coords.longitude;
        locationLabel.value = 'Votre position · Localisée';
        initialiserCarte(userLat, userLon);
      },
      (error) => {
        console.warn("Géolocalisation refusée ou indisponible.", error);
        locationLabel.value = 'Position estimée';
        initialiserCarte(userLat, userLon);
      },
      { timeout: 10000 }
    );
  } else {
    initialiserCarte(userLat, userLon);
  }
}

async function initialiserCarte(lat, lon, demo = false) {
  await nextTick();

  if (!mapContainer.value) return;

  if (!map) {
    map = L.map(mapContainer.value, { zoomControl: false }).setView([lat, lon], 13);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      className: 'map-tiles'
    }).addTo(map);
  } else {
    map.setView([lat, lon], 13);
  }

  if (positionMarker) map.removeLayer(positionMarker);
  positionMarker = L.marker([lat, lon], {
    icon: L.divIcon({
      className: 'position-marker-wrap',
      html: '<div class="position-marker"><span></span></div>',
      iconSize: [36, 36],
      iconAnchor: [18, 18]
    })
  }).addTo(map);

  if (demo) {
    isLoading.value = false;
    dessinerZone();
  } else {
    chargerDonneesBackend(lat, lon);
  }
}

function chargerDonneesBackend(lat, lon) {
  const token = localStorage.getItem('token');
  const url = `https://twincare-t2xu.onrender.com/api/environnement/donnees?lat=${lat}&lon=${lon}`;

  fetch(url, {
    method: "GET",
    headers: { "Authorization": "Bearer " + token }
  })
  .then(res => res.json())
  .then(data => {
    envData.value = data;
    isLoading.value = false;
    dessinerZone();
  })
  .catch(err => {
    console.error("Erreur API Environnement:", err);
    envData.value = demoEnvData;
    isLoading.value = false;
    dessinerZone();
  });
}

function dessinerZone() {
  if (!map) return;

  if (currentLayer !== null) {
    map.removeLayer(currentLayer);
  }

  const couleurActuelle = modeActif.value === 'pollen' ? envData.value.pollenColor : envData.value.airColor;

  currentLayer = L.circle([userLat, userLon], {
    stroke: false,
    fillColor: couleurActuelle,
    fillOpacity: 0.35,
    radius: modeActif.value === 'pollen' ? 4000 : 5000 
  }).addTo(map);
}

function basculerMode() {
  modeActif.value = modeActif.value === 'pollen' ? 'air' : 'pollen';
  dessinerZone();
}

onMounted(() => {
  demanderGeolocalisation();
});

onUnmounted(() => {
  if (map) {
    map.remove();
    map = null;
  }
});
</script>

<style scoped>
.environment-page {
  position: relative;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: #eaf3f7;
}

.map-container {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.top-controls {
  position: absolute;
  top: 34px;
  left: 0;
  width: 100%;
  padding: 0 14px;
  display: flex;
  align-items: center;
  z-index: 1000;
}

.control-btn {
  width: 46px !important;
  height: 46px !important;
  color: #37474F;
}

.status-pill {
  max-width: 250px;
  min-height: 50px;
}

.status-title {
  font-size: 0.92rem;
  color: #37474F;
}

.location-chip {
  position: absolute;
  z-index: 800;
  top: 98px;
  left: 16px;
  background: rgba(255, 255, 255, 0.94);
  border-radius: 18px;
  padding: 7px 11px;
  display: flex;
  align-items: center;
  color: #37474F;
  font-size: 0.72rem;
  font-weight: 700;
  box-shadow: 0 4px 14px rgba(25, 55, 70, 0.18);
}

.live-dot {
  width: 7px;
  height: 7px;
  background: #36B37E;
  border-radius: 50%;
  margin-left: 7px;
  box-shadow: 0 0 0 3px rgba(54, 179, 126, 0.18);
}

.map-legend {
  position: absolute;
  top: 142px;
  right: 14px;
  z-index: 800;
  width: 118px;
  padding: 9px 10px;
  border-radius: 13px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 4px 14px rgba(25, 55, 70, 0.16);
}

.legend-title {
  font-size: 0.68rem;
  font-weight: 800;
  color: #37474F;
  margin-bottom: 6px;
}

.legend-scale {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  height: 6px;
  border-radius: 5px;
  overflow: hidden;
}

.legend-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 0.52rem;
  color: #6B7280;
}

.environment-sheet {
  position: absolute;
  z-index: 900;
  left: 14px;
  right: 14px;
  bottom: 84px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.97) !important;
  backdrop-filter: blur(12px);
}

.sheet-kicker {
  color: #2A93D5;
  font-size: 0.61rem;
  letter-spacing: 0.08em;
  font-weight: 900;
}

.sheet-title {
  color: #37474F;
  font-size: 1.05rem;
  line-height: 1.15;
  font-weight: 900;
}

.pollen-grid {
  display: grid;
  gap: 10px;
}

.pollen-name, .pollen-level {
  font-size: 0.72rem;
  font-weight: 700;
}

.pollen-name { color: #455A64; }
.pollen-level { font-size: 0.66rem; }

.pollen-track {
  height: 6px;
  margin-top: 4px;
  border-radius: 6px;
  background: #E8EEF1;
  overflow: hidden;
}

.pollen-track span {
  display: block;
  height: 100%;
  border-radius: 6px;
}

.air-summary {
  display: flex;
  align-items: center;
  margin-top: 13px;
  padding-top: 11px;
  border-top: 1px solid #E7ECEF;
  color: #455A64;
  font-size: 0.72rem;
}

:deep(.position-marker-wrap) {
  background: transparent;
  border: none;
}

:deep(.position-marker) {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(42, 147, 213, 0.22);
  display: grid;
  place-items: center;
  animation: pulse 2s infinite;
}

:deep(.position-marker span) {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #2A93D5;
  border: 3px solid white;
  box-shadow: 0 2px 8px rgba(21, 72, 102, 0.35);
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(42, 147, 213, 0.25); }
  50% { box-shadow: 0 0 0 9px rgba(42, 147, 213, 0); }
}

:deep(.leaflet-control-attribution) {
  display: none !important;
}
</style>
