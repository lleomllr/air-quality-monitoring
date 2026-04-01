package com.type;

public class StationAqiPair {
    private String stationID; 
    private Double aqi; 

    public StationAqiPair() {}

    public StationAqiPair(String stationID, Double aqi) {
        this.stationID = stationID; 
        this.aqi = aqi; 
    }

    public String getStationID() { return stationID; }
    public void setStationID(String stationID) { this.stationID = stationID; }

    public Double getAqi() { return aqi; }
    public void setAqi(Double aqi) { this.aqi = aqi; }
}
