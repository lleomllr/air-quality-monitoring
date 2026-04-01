package com.example.demo.service;

import com.example.demo.model.aqi.*;
import com.example.demo.repository.aqi.*;
import com.example.demo.dto.CityGeoMeasurementDTO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service pour gérer les données AQI en temps réel depuis Kafka Connect.
 * Inclut les opérations de lecture et d'écriture pour TimescaleDB.
 */
@Service
public class AqiService {

    @Autowired
    private CityAqiRepository cityAqiRepository;

    @Autowired
    private AvgCityHourRepository avgCityHourRepository;

    @Autowired
    private AvgStateHourRepository avgStateHourRepository;

    @Autowired
    private AvgCity8HourRepository avgCity8HourRepository;

    @Autowired
    private AvgCityDayRepository avgCityDayRepository;

    @Autowired
    private AqiAlertRepository aqiAlertRepository;

    @Autowired
    private MaxCityHourRepository maxCityHourRepository;

    @Autowired
    private MaxStateHourRepository maxStateHourRepository;

    private static final Logger logger = LoggerFactory.getLogger(AqiService.class);

    public List<CityAqi> getLatestCityData(int limit) {
        logger.info("Fetching latest {} city data", limit);
        List<CityAqi> results = cityAqiRepository.findAll(PageRequest.of(0, limit)).getContent();
        logger.info("Found {} city measurements", results.size());
        return results;
    }

    public List<CityGeoMeasurementDTO> getLatestCityGeoData() {
        logger.info("Fetching all latest city geo measurements");
        return cityAqiRepository.findLatestWithCoordinates().stream()
                .map(row -> new CityGeoMeasurementDTO(
                        row.getTimestamp(),
                        row.getState(),
                        row.getCity(),
                        row.getPollutant(),
                        row.getConcentration(),
                        row.getAqi(),
                        row.getCategory(),
                        row.getLongitude(),
                        row.getLatitude()
                ))
                .collect(Collectors.toList());
    }

    public List<AvgCityHour> getCityHourlyAverages(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return avgCityHourRepository.findAll().stream()
                .filter(avg -> avg.getTimeStart().isAfter(since))
                .collect(Collectors.toList());
    }

    public List<AvgStateHour> getStateHourlyAverages(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return avgStateHourRepository.findAll().stream()
                .filter(avg -> avg.getTimeStart().isAfter(since))
                .collect(Collectors.toList());
    }

    public List<AvgCity8Hour> getCity8HourAverages(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return avgCity8HourRepository.findAll().stream()
                .filter(avg -> avg.getTimeStart().isAfter(since))
                .collect(Collectors.toList());
    }

    public List<AvgCityDay> getCityDailyAverages(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return avgCityDayRepository.findAll().stream()
                .filter(avg -> avg.getTimeStart().isAfter(since))
                .collect(Collectors.toList());
    }

    public List<AqiAlert> getActiveAlerts(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return aqiAlertRepository.findAll().stream()
                .filter(alert -> alert.getTime().isAfter(since))
                .collect(Collectors.toList());
    }

    public List<MaxCityHour> getCityMaxHourly(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return maxCityHourRepository.findAll().stream()
                .filter(max -> max.getTimeStart().isAfter(since))
                .collect(Collectors.toList());
    }

    public List<CityAqi> getCityData(String city, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return cityAqiRepository.findAll().stream()
                .filter(data -> data.getCity().equalsIgnoreCase(city) && data.getTime().isAfter(since))
                .collect(Collectors.toList());
    }

    public List<AvgStateHour> getStateData(String state, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return avgStateHourRepository.findAll().stream()
                .filter(data -> data.getState().equalsIgnoreCase(state) && data.getTimeStart().isAfter(since))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getDashboardSummary() {
        // Nombre total de mesures stockées (méthode de base Spring Data)
        long totalMeasurements = cityAqiRepository.count();

        // Nombre de villes uniques
        long totalCities = cityAqiRepository.countDistinctCities();

        // Moyenne AQI (on gère le cas où la base serait vide avec un null check)
        Double avgAqi = cityAqiRepository.calculateAverageAqi24h();
        long averageAqiFormatted = (avgAqi != null) ? Math.round(avgAqi) : 0;

        // Nombre d'alertes des dernières 24h
        //long alertCount = aqiAlertRepository.countRecentAnomalies24h();

        // On renvoie un Map qui correspond exactement aux clés attendues par ton Dashboard React
        return Map.of(
                "totalMeasurements", totalMeasurements,
                "totalCities", totalCities,
                "averageAqi", averageAqiFormatted,
                "alerts", 0 // Placeholder pour les alertes, à remplacer par alertCount
                //"alertCount", alertCount
        );
    }

    public List<Map<String, Object>> getAirQualityTrends() {
        // 1. On récupère les 3 dernières mesures enregistrées dans la base
        List<CityAqi> latestMeasurements = cityAqiRepository.findLatest().stream().limit(3).toList();
        List<Map<String, Object>> trends = new ArrayList<>();

        int idCounter = 1;
        for (CityAqi current : latestMeasurements) {
            // 2. ON UTILISE LA BD TIMESCALE ! On interroge la table pré-calculée avg_city_hour
            List<AvgCityHour> history = avgCityHourRepository.findLastTwoHours(current.getCity(), current.getPollutant());

            double currentAvg = current.getAqi(); // Valeur par défaut
            double previousAvg = currentAvg;

            if (history != null && !history.isEmpty()) {
                currentAvg = history.get(0).getAqi(); // La moyenne de la dernière heure
                if (history.size() > 1) {
                    previousAvg = history.get(1).getAqi(); // La moyenne de l'heure précédente !
                }
            }

            // 3. Calcul du pourcentage d'évolution
            double change = currentAvg - previousAvg;
            double percent = (previousAvg == 0) ? 0 : (change / previousAvg) * 100.0;

            String direction = change > 0 ? "INCREASING" : (change < 0 ? "DECREASING" : "STABLE");
            String desc = change > 0 ? "Dégradation (AQI en hausse)" : (change < 0 ? "Amélioration" : "Stable");

            // On ajoute la tendance à la liste
            trends.add(Map.of(
                    "id", idCounter++,
                    "location", current.getCity(),
                    "pollutant", current.getPollutant(),
                    "direction", direction,
                    "changePercentage", percent,
                    "currentValue", currentAvg,
                    "previousValue", previousAvg,
                    "description", desc,
                    "timestamp", current.getTime().toString()
            ));
        }
        return trends;
    }

    public List<String> getAvailableCities() {
        return cityAqiRepository.findAll().stream()
                .map(CityAqi::getCity)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<String> getAvailableStates() {
        return cityAqiRepository.findAll().stream()
                .map(CityAqi::getState)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    // ==========================
    // Méthodes d’écriture
    // ==========================

    @Transactional("timescaleTransactionManager")
    public CityAqi saveCityAqi(CityAqi data) {
        return cityAqiRepository.save(data);
    }

    @Transactional("timescaleTransactionManager")
    public void saveAllCityAqi(List<CityAqi> dataList) {
        cityAqiRepository.saveAll(dataList);
    }

    @Transactional("timescaleTransactionManager")
    public AvgCityHour saveAvgCityHour(AvgCityHour avg) {
        return avgCityHourRepository.save(avg);
    }

    @Transactional("timescaleTransactionManager")
    public AvgStateHour saveAvgStateHour(AvgStateHour avg) {
        return avgStateHourRepository.save(avg);
    }

    @Transactional("timescaleTransactionManager")
    public AvgCity8Hour saveAvgCity8Hour(AvgCity8Hour avg) {
        return avgCity8HourRepository.save(avg);
    }

    @Transactional("timescaleTransactionManager")
    public AvgCityDay saveAvgCityDay(AvgCityDay avg) {
        return avgCityDayRepository.save(avg);
    }

    @Transactional("timescaleTransactionManager")
    public AqiAlert saveAqiAlert(AqiAlert alert) {
        return aqiAlertRepository.save(alert);
    }

    @Transactional("timescaleTransactionManager")
    public MaxCityHour saveMaxCityHour(MaxCityHour max) {
        return maxCityHourRepository.save(max);
    }

    @Transactional("timescaleTransactionManager")
    public MaxStateHour saveMaxStateHour(MaxStateHour max) {
        return maxStateHourRepository.save(max);
    }
}