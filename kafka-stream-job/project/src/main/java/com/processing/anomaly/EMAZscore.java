package com.processing.anomaly;

import com.type.avro.AnomalyAlert; 
import com.type.avro.ForecastAlert;
import com.type.avro.PreProcessedSchema;
import com.type.avro.StationAnalysisState;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class EMAZscore {

    public static final String INPUT_TOPIC = "pre-processed-all";
    public static final String ANOMALY_TOPIC = "aqi-anomaly-alerts-emazscore-v3";
    public static final String FORECAST_TOPIC = "aqi-forecast-alerts";

    private static final double Z_ALPHA = 0.1; 
    private static final double Z_THRESHOLD = 3.0; 

    private static final double ALPHA_TREND = 0.2; 
    private static final int FORECAST_STEPS = 2; 
    private static final double CRITIC_AQI = 100.0; 

    private static final int MIN_EVENTS = 20;  

    public static Topology buildTopology(Properties streamsConfig) {
        StreamsBuilder builder = new StreamsBuilder(); 

        Map<String, String> serdeConfig = Collections.singletonMap(
            "schema.registry.url", streamsConfig.getProperty("schema.registry.url")
        ); 

        SpecificAvroSerde<PreProcessedSchema> preProcessedSerde = new SpecificAvroSerde<>(); 
        preProcessedSerde.configure(serdeConfig, false); 

        SpecificAvroSerde<StationAnalysisState> stateSerde = new SpecificAvroSerde<>();
        stateSerde.configure(serdeConfig, false);

        SpecificAvroSerde<AnomalyAlert> anomalySerde = new SpecificAvroSerde<>();
        anomalySerde.configure(serdeConfig, false);

        SpecificAvroSerde<ForecastAlert> forecastSerde = new SpecificAvroSerde<>();
        forecastSerde.configure(serdeConfig, false);

        KStream<String, PreProcessedSchema> rawStream = builder.stream(
            INPUT_TOPIC, 
            Consumed.with(Serdes.String(), preProcessedSerde)
        );

        KStream<String, StationAnalysisState> analyzedStream = rawStream
            .selectKey((key, value) -> value.getCity().toString() + " - " + value.getState().toString())
            .groupByKey(Grouped.with(Serdes.String(), preProcessedSerde))
            .aggregate(
                () -> StationAnalysisState.newBuilder()
                        .setStationId("").setPollutant("").setCurrentAqi(0.0).setCount(0L)
                        .setEma(0.0).setEmvar(0.0).setLastZScore(0.0).setIsAnomaly(false)
                        .setEma1(0.0).setEma2(0.0).setPredictedAqi(0.0).setTrend(0.0).setIsForecastAlert(false)
                        .build(), 
                (stationId, newValue, state) -> {
                    double currentAQI = (double) newValue.getAqi(); 
                    String pollutant = newValue.getPollutant() != null ? newValue.getPollutant().toString() : "unknown";
                    long newCount = state.getCount() + 1; 
                    
                    //Variables Z-Score
                    double newEma, newEmvar, zscore = 0.0; boolean isAnomaly = false;
                    //Variables DEMA (Trend)
                    double ema1, ema2, predictedAqi = 0.0, trend = 0.0; boolean isForecastAlert = false;

                    if (state.getCount() == 0) {
                        newEma = currentAQI; newEmvar = 0.0;
                        ema1 = currentAQI; ema2 = currentAQI;
                    } else {
                        //Z-SCORE
                        double diff = currentAQI - state.getEma(); 
                        if (state.getEmvar() > 0) {
                            zscore = Math.abs(diff) / (Math.sqrt(state.getEmvar())); 
                        }
                        if (newCount > MIN_EVENTS && zscore > Z_THRESHOLD) {
                            isAnomaly = true; 
                        }
                        newEma = state.getEma() + Z_ALPHA * diff; 
                        newEmvar = (1 - Z_ALPHA) * (state.getEmvar() + Z_ALPHA * diff * diff); 

                        //DEMA (Trend)
                        ema1 = ALPHA_TREND * currentAQI + (1 - ALPHA_TREND) * state.getEma1();
                        ema2 = ALPHA_TREND * ema1 + (1 - ALPHA_TREND) * state.getEma2();
                        
                        if (newCount > MIN_EVENTS) {
                            double a = 2 * ema1 - ema2;
                            trend = (ALPHA_TREND / (1 - ALPHA_TREND)) * (ema1 - ema2);
                            predictedAqi = a + (trend * FORECAST_STEPS);
                            
                            if (currentAQI < CRITIC_AQI && predictedAqi >= CRITIC_AQI) {
                                isForecastAlert = true;
                            }
                        }
                    }

                    return StationAnalysisState.newBuilder()
                        .setStationId(stationId)
                        .setPollutant(pollutant)
                        .setCurrentAqi(currentAQI)
                        .setCount(newCount)
                        .setEma(newEma).setEmvar(newEmvar).setLastZScore(zscore).setIsAnomaly(isAnomaly)
                        .setEma1(ema1).setEma2(ema2).setPredictedAqi(predictedAqi).setTrend(trend).setIsForecastAlert(isForecastAlert)
                        .build();
                }, 
                Materialized.with(Serdes.String(), stateSerde)
            ).toStream();

        analyzedStream
            .filter((stationId, state) -> state.getIsAnomaly())
            .map((stationId, state) -> {
                AnomalyAlert alert = AnomalyAlert.newBuilder()
                    .setStationId(stationId)
                    .setPollutant(state.getPollutant())
                    .setAqi(state.getCurrentAqi())
                    .setExpectedAqi(state.getEma())
                    .setZScore(state.getLastZScore())
                    .setTimestamp(System.currentTimeMillis())
                    .build(); 
                return KeyValue.pair(stationId, alert); 
            })
            .to(ANOMALY_TOPIC, Produced.with(Serdes.String(), anomalySerde)); 

        analyzedStream
            .filter((stationId, state) -> state.getIsForecastAlert())
            .map((stationId, state) -> {
                ForecastAlert alert = ForecastAlert.newBuilder()
                    .setStationId(stationId)
                    .setPollutant(state.getPollutant())
                    .setCurrentAqi(state.getCurrentAqi())
                    .setPredictedAqi(Math.round(state.getPredictedAqi() * 100.0) / 100.0)
                    .setTrend(Math.round(state.getTrend() * 100.0) / 100.0)
                    .setForecastSteps(FORECAST_STEPS)
                    .setTimestamp(System.currentTimeMillis())
                    .build();
                return KeyValue.pair(stationId, alert);
            })
            .to(FORECAST_TOPIC, Produced.with(Serdes.String(), forecastSerde));
        return builder.build(); 
    }

    public static void main(String[] args) {
        Properties props = new Properties(); 
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "aqi-anomaly-detection-v1");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        props.put("schema.registry.url", "http://schema-registry:8081");

        KafkaStreams streams = new KafkaStreams(EMAZscore.buildTopology(props), props);
        System.out.println("Démarrage du job de détection d'anomalies (Analyse + Prévisions)");
        streams.start();

        final CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            streams.close();
            latch.countDown();
        }));

        try {
            latch.await(); 
        } catch (InterruptedException e) {
            streams.close(); 
            Thread.currentThread().interrupt();
        }


    }
    
}
