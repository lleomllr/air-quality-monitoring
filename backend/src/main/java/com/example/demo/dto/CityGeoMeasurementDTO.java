package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CityGeoMeasurementDTO {
    private LocalDateTime timestamp;
    private String state;
    private String city;
    private String pollutant;
    private Float concentration;
    private Short aqi;
    private String category;
    private Double longitude;
    private Double latitude;
}
