package com.example.demo.repository.aqi;

import com.example.demo.model.aqi.AvgState8Hour;
import com.example.demo.model.aqi.TimeStartStatePollutantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AvgState8HourRepository extends JpaRepository<AvgState8Hour, TimeStartStatePollutantId> {
    // Ajoute ici les méthodes spécifiques si besoin
}
