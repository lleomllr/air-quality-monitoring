package com.example.demo.repository.aqi;

import com.example.demo.model.aqi.MaxStateHour;
import com.example.demo.model.aqi.TimeStartStatePollutantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MaxStateHourRepository extends JpaRepository<MaxStateHour, TimeStartStatePollutantId> {

    /**
     * Trouver les maximums horaires d'un état après une date donnée
     */
    @Query("SELECT m FROM MaxStateHour m WHERE m.state = :state AND m.timeStart >= :since ORDER BY m.timeStart DESC")
    List<MaxStateHour> findByStateAndTimeStartAfter(String state, LocalDateTime since);
}

