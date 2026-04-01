package com.example.demo.repository.aqi;

import com.example.demo.model.aqi.CityLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CityLocationRepository extends JpaRepository<CityLocation, String> {
}
