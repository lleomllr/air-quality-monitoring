package com.example.demo.repository.aqi;

import com.example.demo.model.aqi.AvgCityDay;
import com.example.demo.model.aqi.TimeStartCityPollutantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AvgCityDayRepository extends JpaRepository<AvgCityDay, TimeStartCityPollutantId> {

    /**
     * Trouver les moyennes journalières d'une ville après une date donnée
     */
    @Query("SELECT a FROM AvgCityDay a WHERE a.city = :city AND a.timeStart >= :since ORDER BY a.timeStart DESC")
    List<AvgCityDay> findByCityAndTimeStartAfter(String city, LocalDateTime since);
}

