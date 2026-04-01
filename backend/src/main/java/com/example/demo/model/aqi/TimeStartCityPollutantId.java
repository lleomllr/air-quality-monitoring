package com.example.demo.model.aqi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeStartCityPollutantId implements Serializable {
    private LocalDateTime timeStart;
    private String city;
    private String pollutant;
}
