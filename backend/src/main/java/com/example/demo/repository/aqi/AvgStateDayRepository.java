package com.example.demo.repository.aqi;

import com.example.demo.model.aqi.AvgStateDay;
import com.example.demo.model.aqi.TimeStartStatePollutantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AvgStateDayRepository extends JpaRepository<AvgStateDay, TimeStartStatePollutantId> {
    // Ajoute ici les méthodes spécifiques si besoin
}
