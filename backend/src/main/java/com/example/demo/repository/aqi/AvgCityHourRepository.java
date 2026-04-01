package com.example.demo.repository.aqi;

import com.example.demo.model.aqi.AvgCityHour;
import com.example.demo.model.aqi.TimeStartCityPollutantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AvgCityHourRepository extends JpaRepository<AvgCityHour, TimeStartCityPollutantId> {
    // Récupère les 2 dernières moyennes horaires pour comparer
    @Query(value = "SELECT * FROM aqi.avg_city_hour " +
            "WHERE city = :city AND pollutant = :pollutant " +
            "ORDER BY time_start DESC LIMIT 2", nativeQuery = true)
    List<AvgCityHour> findLastTwoHours(@Param("city") String city, @Param("pollutant") String pollutant);

    // Récupère les 5 mesures les plus proches de la localisation utilisateur
    @Query(value = "SELECT ach.* FROM aqi.avg_city_hour ach " +
            "JOIN aqi.city_location cl ON ach.city = cl.city AND ach.state = cl.state " +
            "ORDER BY cl.geom <-> :userLocation LIMIT 5", nativeQuery = true)
    List<AvgCityHour> findNearestAqi(@Param("userLocation") org.locationtech.jts.geom.Point userLocation);
}
