package com.example.demo.repository.aqi;

import com.example.demo.model.aqi.MaxCityHour;
import com.example.demo.model.aqi.TimeStartCityPollutantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MaxCityHourRepository extends JpaRepository<MaxCityHour, TimeStartCityPollutantId> {

    /**
     * Trouver les maximums horaires d'une ville après une date donnée
     */
    @Query("SELECT m FROM MaxCityHour m WHERE m.city = :city AND m.timeStart >= :since ORDER BY m.timeStart DESC")
    List<MaxCityHour> findByCityAndTimeStartAfter(String city, LocalDateTime since);
}

