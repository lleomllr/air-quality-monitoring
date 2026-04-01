package com.example.demo.model.aqi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeCityPollutantId implements Serializable {
    private LocalDateTime time;
    private String city;
    private String pollutant;
}
