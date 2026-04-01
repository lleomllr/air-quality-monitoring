package com.example.demo.controller;

import com.example.demo.model.aqi.*;
import com.example.demo.model.client.User;
import com.example.demo.dto.CityGeoMeasurementDTO;
import com.example.demo.dto.GlobalTopKEntryDTO;

import com.example.demo.repository.aqi.CityAqiRepository;
import com.example.demo.repository.client.UserRepository;
import com.example.demo.repository.aqi.AqiAlertRepository;
import com.example.demo.repository.aqi.CityLocationRepository;

import com.example.demo.service.AqiService;
import com.example.demo.service.GlobalTopKService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

/**
 * REST Controller for real-time AQI data from Kafka Connect.
 *
 * Ces endpoints récupèrent les données en temps réel depuis TimescaleDB
 * qui sont alimentées par Kafka Connect Sink.
 */
@RestController
@RequestMapping("/api/aqi")
@CrossOrigin(origins = "*")
public class AqiController {

    @Autowired
    private AqiService aqiService;

    @Autowired
    private AqiAlertRepository aqiAlertRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CityAqiRepository cityAqiRepository;

    @Autowired
    private CityLocationRepository cityLocationRepository;

    @Autowired
    private GlobalTopKService globalTopKService;

    /**
     * Obtenir les dernières mesures par ville
     */
    @GetMapping("/cities/latest/geo")
    public ResponseEntity<List<CityGeoMeasurementDTO>> getLatestCityGeoData() {
        return ResponseEntity.ok(aqiService.getLatestCityGeoData());
    }

    /**
     * Obtenir les moyennes horaires par ville
     */
    @GetMapping("/cities/hourly")
    public ResponseEntity<List<AvgCityHour>> getCityHourlyAverages(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(aqiService.getCityHourlyAverages(hours));
    }

    /**
     * Obtenir les moyennes horaires par état
     */
    @GetMapping("/states/hourly")
    public ResponseEntity<List<AvgStateHour>> getStateHourlyAverages(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(aqiService.getStateHourlyAverages(hours));
    }

    /**
     * Obtenir les moyennes sur 8 heures par ville
     */
    @GetMapping("/cities/8hour")
    public ResponseEntity<List<AvgCity8Hour>> getCity8HourAverages(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(aqiService.getCity8HourAverages(days));
    }

    /**
     * Obtenir les moyennes journalières par ville
     */
    @GetMapping("/cities/daily")
    public ResponseEntity<List<AvgCityDay>> getCityDailyAverages(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(aqiService.getCityDailyAverages(days));
    }

    /**
     * Obtenir les maximums horaires par ville
     */
    @GetMapping("/cities/max/hourly")
    public ResponseEntity<List<MaxCityHour>> getCityMaxHourly(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(aqiService.getCityMaxHourly(hours));
    }

    /**
     * Obtenir les données d'une ville spécifique
     */
    @GetMapping("/cities/{city}")
    public ResponseEntity<List<CityAqi>> getCityData(
            @PathVariable String city,
            @RequestParam(defaultValue = "24") int hours) {
                if ("latest".equalsIgnoreCase(city)) {
                    return ResponseEntity.notFound().build();
                }
        return ResponseEntity.ok(aqiService.getCityData(city, hours));
    }

    /**
     * Obtenir les localisations géographiques des villes (pour la carte)
     */
    @GetMapping("/city-locations")
    public ResponseEntity<?> getCityLocations() {
        // On construit manuellement la liste avec une HashMap
        // Cela garantit aucun crash lié au Point géographique ou aux valeurs nulles !
        java.util.List<java.util.Map<String, Object>> safeCities = cityLocationRepository.findAll().stream()
                .map(c -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("city", c.getCity());
                    map.put("state", c.getState());
                    map.put("latitude", c.getLatitude());
                    map.put("longitude", c.getLongitude());
                    return map;
                })
                .toList();

        return ResponseEntity.ok(safeCities);
    }

    /**
     * Obtenir les données d'un état spécifique
     */
    @GetMapping("/states/{state}")
    public ResponseEntity<List<AvgStateHour>> getStateData(
            @PathVariable String state,
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(aqiService.getStateData(state, hours));
    }

    /**
     * Obtenir la liste des villes disponibles
     */
    @GetMapping("/cities")
    public ResponseEntity<List<String>> getAvailableCities() {
        return ResponseEntity.ok(aqiService.getAvailableCities());
    }

    /**
     * Obtenir la liste des états disponibles
     */
    @GetMapping("/states")
    public ResponseEntity<List<String>> getAvailableStates() {
        return ResponseEntity.ok(aqiService.getAvailableStates());
    }

    /**
     * Obtenir les alertes locales pour l'utilisateur connecté
     * Les alertes sont filtrées par proximité géographique (ex: 50 km) et par date (ex: dernières 24h).
     */
    @GetMapping("/alerts")
    public ResponseEntity<?> getAlerts(
            @RequestParam(defaultValue = "24") int hours,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        // Calculer l'heure limite
        LocalDateTime since = LocalDateTime.now().minusHours(hours);

        // Si l'utilisateur n'est pas connecté, on renvoie une liste vide
        if (userDetails == null) {
            return ResponseEntity.ok(List.of());
        }

        // Récupérer l'utilisateur
        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);

        if (user == null || user.getLocation() == null) {
            return ResponseEntity.ok(List.of()); // Pas de position = pas d'alertes locales
        }

        // Chercher les alertes dans un rayon de 50 km (50 000 mètres)
        List<AqiAlert> localAlerts = aqiAlertRepository.findNearbyAlerts(
                since,
                user.getLocation().getX(), // Longitude (X)
                user.getLocation().getY(), // Latitude (Y)
                50000.0
        );

        return ResponseEntity.ok(localAlerts);
    }

    /**
     * Obtenir TOUTES les alertes du pays des dernières X heures
     */
    @GetMapping("/alerts/all")
    public ResponseEntity<?> getAllAlerts(@RequestParam(defaultValue = "24") int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);

        // On récupère tout, mais on filtre en Java pour ne garder que les récentes
        List<AqiAlert> recentAlerts = aqiAlertRepository.findAll().stream()
                .filter(alert -> alert.getTime() != null && alert.getTime().isAfter(since))
                .sorted((a1, a2) -> a2.getTime().compareTo(a1.getTime()))
                .toList();

        return ResponseEntity.ok(recentAlerts);
    }

    @GetMapping("/summary")
    public ResponseEntity<?> getSummary() {
        Map<String, Object> summaryData = aqiService.getDashboardSummary();
        return ResponseEntity.ok(summaryData);
    }

    @GetMapping("/trends")
    public ResponseEntity<?> getTrends() {
        // On récupère désormais les vraies tendances calculées par AqiService !
        List<Map<String, Object>> trends = aqiService.getAirQualityTrends();
        return ResponseEntity.ok(trends);
    }

    /**
     * Endpoint pour obtenir les mesures AQI les plus proches de l'utilisateur connecté.
     * Utilise la géolocalisation stockée dans la table clients pour trouver les données les plus pertinentes.
     */
    @GetMapping("/near-me")
    public ResponseEntity<List<CityAqi>> getAqiNearUser(Principal principal) {
        // Récupérer l'utilisateur via son email (principal.getName())
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Utiliser la requête spatiale du Repository
        if (user.getLocation() != null) {
            return ResponseEntity.ok(cityAqiRepository.findNearestAqi(user.getLocation()));
        }

        // Fallback si pas de position : on renvoie les dernières mesures classiques
        return ResponseEntity.ok(aqiService.getLatestCityData(5));
    }
    /**
     * Endpoint pour obtenir le top K des villes les plus polluées au niveau global.
     * Ce classement est calculé régulièrement par un service dédié qui agrège les données en temps réel.
     */
    @GetMapping("/topk/global")
    public ResponseEntity<List<GlobalTopKEntryDTO>> getGlobalTopK(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(globalTopKService.getLatestTopK(limit));
    }

}