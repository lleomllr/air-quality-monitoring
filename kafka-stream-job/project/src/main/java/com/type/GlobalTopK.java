package com.type;
import java.util.*;

public class GlobalTopK {
    private final int maxK; 
    private final Map<String, Double> currentReading = new HashMap<>(); 

    public GlobalTopK() {
        this.maxK = 10; 
    }

    public GlobalTopK(int maxK) {
        this.maxK = maxK;
    }

    public GlobalTopK add(String stationID, Double value) {
        currentReading.put(stationID, value); 
        return keepOnlyTopK(); 
    }

    public GlobalTopK remove(String stationID) {
        currentReading.remove(stationID); 
        return this; 
    }

    private GlobalTopK keepOnlyTopK() {
        if (currentReading.size() <= maxK) return this; 

        List<Map.Entry<String, Double>> list = new ArrayList<>(currentReading.entrySet()); 
        list.sort(Map.Entry.<String, Double>comparingByValue().reversed()); 

        currentReading.clear(); 
        for (int i=0; i < maxK; i++) {
            currentReading.put(list.get(i).getKey(), list.get(i).getValue()); 
        }
        return this; 
    }

    public Map<String, Double> getTopK() {
        return Collections.unmodifiableMap(currentReading); 
    }
    
}
