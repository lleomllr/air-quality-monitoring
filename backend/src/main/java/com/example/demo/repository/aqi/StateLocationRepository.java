package com.example.demo.repository.aqi;

import com.example.demo.model.aqi.StateLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StateLocationRepository extends JpaRepository<StateLocation, String> {
}
