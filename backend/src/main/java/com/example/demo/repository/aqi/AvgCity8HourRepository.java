package com.example.demo.repository.aqi;

import com.example.demo.model.aqi.AvgCity8Hour;
import com.example.demo.model.aqi.TimeStartCityPollutantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AvgCity8HourRepository extends JpaRepository<AvgCity8Hour, TimeStartCityPollutantId> {

    /**
     * Trouver les moyennes 8h d'une ville après une date donnée
     */
    @Query("SELECT a FROM AvgCity8Hour a WHERE a.city = :city AND a.timeStart >= :since ORDER BY a.timeStart DESC")
    List<AvgCity8Hour> findByCityAndTimeStartAfter(String city, LocalDateTime since);
}

