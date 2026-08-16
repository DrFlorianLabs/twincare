package backend.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import backend.dto.EnvironnementDTO;

@Service
@Profile("legacy-environnement")
public class EnvironnementService {

    public EnvironnementDTO getDonneesEnvironnement(double lat, double lon) {
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=" + lat + "&longitude=" + lon + "&current=european_aqi,grass_pollen";

        EnvironnementDTO dto = new EnvironnementDTO();

        try {
            JsonNode response = restTemplate.getForObject(url, JsonNode.class);
            if (response != null && response.has("current")) {
                JsonNode current = response.get("current");

                int aqi = current.get("european_aqi").asInt();
                double pollen = current.get("grass_pollen").asDouble();

                if (aqi < 50) {
                    dto.setAirStatus("Qualité bonne");
                    dto.setAirColor("#4caf50");
                } else if (aqi < 100) {
                    dto.setAirStatus("Qualité moyenne");
                    dto.setAirColor("#fbc02d");
                } else {
                    dto.setAirStatus("Qualité mauvaise");
                    dto.setAirColor("#f44336");
                }

                if (pollen < 10) {
                    dto.setPollenStatus("Taux faible");
                    dto.setPollenColor("#4caf50");
                } else if (pollen < 50) {
                    dto.setPollenStatus("Taux modéré");
                    dto.setPollenColor("#fbc02d");
                } else {
                    dto.setPollenStatus("Taux élevé");
                    dto.setPollenColor("#f44336");
                }
            }
        } catch (Exception e) {
            dto.setAirStatus("Données indisponibles");
            dto.setAirColor("#9e9e9e");
            dto.setPollenStatus("Données indisponibles");
            dto.setPollenColor("#9e9e9e");
        }

        return dto;
    }
}
