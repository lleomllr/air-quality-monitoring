package com.example.demo.dto;

public class GlobalTopKEntryDTO {

    private final String stationId;
    private final double aqi;
    private final String pollutant;

    public GlobalTopKEntryDTO(String stationId, double aqi, String pollutant) {
        this.stationId = stationId;
        this.aqi = aqi;
        this.pollutant = pollutant;
    }

    public String getStationId() {
        return stationId;
    }

    public double getAqi() {
        return aqi;
    }

    public String getPollutant() {
        return pollutant;
    }
}
