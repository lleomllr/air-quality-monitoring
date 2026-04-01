package com.example.demo.repository.aqi;

import com.example.demo.model.aqi.AvgStateHour;
import com.example.demo.model.aqi.TimeStartStatePollutantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AvgStateHourRepository extends JpaRepository<AvgStateHour, TimeStartStatePollutantId> {

    /**
     * Trouver les moyennes horaires d'un état spécifique après une date donnée
     */
    @Query("SELECT a FROM AvgStateHour a WHERE a.state = :state AND a.timeStart >= :since ORDER BY a.timeStart DESC")
    List<AvgStateHour> findByStateAndTimeStartAfter(String state, LocalDateTime since);

    /**
     * Trouver toutes les moyennes horaires après une date donnée
     */
    @Query("SELECT a FROM AvgStateHour a WHERE a.timeStart >= :since ORDER BY a.timeStart DESC")
    List<AvgStateHour> findByTimeStartAfter(LocalDateTime since);
}

