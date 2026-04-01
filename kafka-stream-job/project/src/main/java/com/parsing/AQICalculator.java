package com.parsing;

public class AQICalculator {
    private int aqi;
    private String pollutantName;
    private String pollutantUnit;
    private int pollutantIndex;
    private double concentration = 0.0;

    //Tables de points de rupture minimum pour chaque polluant
    private static double breakpointTableLow[][] = {
            {0, 0.055, 0.071, 0.086, 0.106, 0.405}, //O3
            {0, 9.1, 35.5, 55.5, 125.5, 225.5},     //PM2.5
            {0, 55, 155, 255, 355, 425},            //PM10
            {0, 51, 101, 151, 201, 301}             //AQI
    };

    //Tables de points de rupture maximum pour chaque polluant
    private static double breakpointTableHigh[][] = {
            {0.054, 0.070, 0.085, 0.105, 0.404, 0.604}, //O3
            {9.0, 35.4, 55.4, 125.4, 225.4, 325.4},     //PM2.5
            {54, 154, 254, 354, 424, 604},              //PM10
            {50, 100, 150, 200, 300, 500}               //AQI
    };

    public AQICalculator(int AQI, String pollutantName) {
        if (AQI < 0) {
            AQI = 0;
        } else if (AQI > 500) {
            AQI = 500;
        }
        this.aqi = AQI;
        this.pollutantName = pollutantName;
        this.pollutantIndex = getPollutantIndex();

        calcConcentration();
    }

    public double getConcentration() {
        return this.concentration;
    }

    public String getPollutantUnit() {
        return this.pollutantUnit;
    }

    public double getBPHigh(int i, int j) {
        return this.breakpointTableHigh[i][j];
    }

    public double getBPLow(int i, int j) {
        return this.breakpointTableLow[i][j];
    }

    //On récupère l'index du polluant dans les tables de points de rupture et son unité
    private int getPollutantIndex() {
        switch (this.pollutantName) {
            case "O3":
                return 0;
            case "PM2.5":
                return 1;
            case "PM10":
                return 2;
            default:
                throw new IllegalArgumentException("Nom de polluant invalide");
        }
    }

    //Calcul de la concentration à partir de l'AQI
    public void calcConcentration() {
        for (int j = 0; j < 6; j++) {
            if (this.aqi >= this.getBPLow(3, j) && this.aqi <= this.getBPHigh(3, j)) {
                double BPHigh = this.getBPHigh(this.pollutantIndex, j);
                double BPLow = this.getBPLow(this.pollutantIndex, j);
                double AQIHigh = this.getBPHigh(3, j);
                double AQILow = this.getBPLow(3, j);
                this.concentration = ((BPHigh - BPLow)/(AQIHigh - AQILow)) * (this.aqi - AQILow) + BPLow;
            }
        }
    }
}