package com.example.demo.repository.aqi;

import com.example.demo.model.aqi.CityAqi;
import com.example.demo.model.aqi.TimeCityPollutantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CityAqiRepository extends JpaRepository<CityAqi, TimeCityPollutantId> {

    interface CityGeoMeasurementProjection {
        LocalDateTime getTimestamp();
        String getState();
        String getCity();
        String getPollutant();
        Float getConcentration();
        Short getAqi();
        String getCategory();
        Double getLongitude();
        Double getLatitude();
    }

    /**
     * Trouver les 5 villes les plus proches de la localisation de l'utilisateur
     * en utilisant la distance géographique (PostGIS)
     */
    @Query(value = "SELECT c.* FROM aqi.city c " +
            "JOIN aqi.city_location cl ON c.city = cl.city AND c.state = cl.state " +
            "ORDER BY cl.geom <-> :location LIMIT 5",
            nativeQuery = true)
    List<CityAqi> findNearestAqi(@Param("location") Point location);

    // 1. Compter le nombre de villes uniques surveillées
    @Query("SELECT COUNT(DISTINCT c.city) FROM CityAqi c")
    long countDistinctCities();

    // 2. Calculer la moyenne de l'AQI sur les 24 dernières heures
    @Query(value = "SELECT COALESCE(AVG(aqi), 0) FROM aqi.city WHERE time >= NOW() - INTERVAL '24 HOURS'", nativeQuery = true)
    Double calculateAverageAqi24h();

    /**
     * Trouver les mesures d'une ville spécifique après une date donnée
     */
    @Query("SELECT c FROM CityAqi c WHERE c.city = :city AND c.time >= :since ORDER BY c.time DESC")
    List<CityAqi> findByCityAndTimeAfter(String city, LocalDateTime since);

    /**
     * Trouver les mesures d'un état spécifique après une date donnée
     */
    @Query("SELECT c FROM CityAqi c WHERE c.state = :state AND c.time >= :since ORDER BY c.time DESC")
    List<CityAqi> findByStateAndTimeAfter(String state, LocalDateTime since);

    /**
     * Trouver les dernières mesures par ville (limit)
     */
    @Query("SELECT c FROM CityAqi c ORDER BY c.time DESC")
    List<CityAqi> findLatest();

        @Query(value = "SELECT c.time AS timestamp, c.state AS state, c.city AS city, c.pollutant AS pollutant, " +
            "c.concentration AS concentration, c.aqi AS aqi, c.category AS category, " +
            "cl.longitude AS longitude, cl.latitude AS latitude " +
            "FROM aqi.city c " +
            "JOIN aqi.city_location cl ON c.city = cl.city AND c.state = cl.state " +
            "ORDER BY c.time DESC " +
            "LIMIT :limit", nativeQuery = true)
        List<CityGeoMeasurementProjection> findLatestWithCoordinates(@Param("limit") int limit);

    /**
     * Trouver les dernières mesures par ville avec leurs coordonnées géographiques (sans limite)
     */
    @Query(value = "SELECT c.time AS timestamp, c.state AS state, c.city AS city, c.pollutant AS pollutant, " +
            "c.concentration AS concentration, c.aqi AS aqi, c.category AS category, " +
            "cl.longitude AS longitude, cl.latitude AS latitude " +
            "FROM aqi.city c " +
            "JOIN aqi.city_location cl ON c.city = cl.city AND c.state = cl.state " +
            "ORDER BY c.time DESC", nativeQuery = true)
    List<CityGeoMeasurementProjection> findLatestWithCoordinates();

    /**
     * Obtenir toutes les villes distinctes
     */
    @Query("SELECT DISTINCT c.city FROM CityAqi c ORDER BY c.city")
    List<String> findDistinctCities();

    /**
     * Obtenir tous les états distincts
     */
    @Query("SELECT DISTINCT c.state FROM CityAqi c ORDER BY c.state")
    List<String> findDistinctStates();
}