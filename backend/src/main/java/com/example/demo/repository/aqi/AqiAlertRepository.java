package com.example.demo.repository.aqi;

import com.example.demo.model.aqi.AqiAlert;
import com.example.demo.model.aqi.TimeCityPollutantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AqiAlertRepository extends JpaRepository<AqiAlert, TimeCityPollutantId> {
    List<AqiAlert> findByTimeAfter(LocalDateTime time);
    List<AqiAlert> findByCity(String city);
    List<AqiAlert> findByState(String state);
    List<AqiAlert> findByTimeAfterAndCity(LocalDateTime time, String city);
    List<AqiAlert> findByTimeAfterAndState(LocalDateTime time, String state);

    // Requête native pour trouver les alertes à proximité d'une localisation donnée
    @Query(value = "SELECT a.* FROM aqi.alerts a " +
            "JOIN aqi.city_location c ON a.city = c.city AND a.state = c.state " +
            "WHERE a.time >= :since " +
            "AND ST_DWithin(c.geom::geography, ST_MakePoint(:lng, :lat)::geography, :radiusMeters)",
            nativeQuery = true)
    List<AqiAlert> findNearbyAlerts(
            @Param("since") LocalDateTime since,
            @Param("lng") double lng,
            @Param("lat") double lat,
            @Param("radiusMeters") double radiusMeters
    );
}

