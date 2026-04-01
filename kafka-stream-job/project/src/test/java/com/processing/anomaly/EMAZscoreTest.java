package com.processing.anomaly;

import com.type.avro.AnomalyAlert;
import com.type.avro.ForecastAlert;
import com.type.avro.PreProcessedSchema;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class EMAZscoreTest {
    private TopologyTestDriver testDriver;
    private TestInputTopic<String, PreProcessedSchema> inputTopic;
    private TestOutputTopic<String, AnomalyAlert> anomalyOutputTopic; 
    private TestOutputTopic<String, ForecastAlert> forecastOutputTopic;

    private static final String MOCK_SCHEMA_REGISTRY_URL = "mock://test-registry";

    @BeforeEach
    public void setup() {
        Properties props = new Properties(); 
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-emazscore");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put("schema.registry.url", MOCK_SCHEMA_REGISTRY_URL);

        Topology topology = EMAZscore.buildTopology(props); 
        testDriver = new TopologyTestDriver(topology, props); 

        Map<String, String> serdeConfig = Collections.singletonMap("schema.registry.url", MOCK_SCHEMA_REGISTRY_URL); 

        SpecificAvroSerde<PreProcessedSchema> inputSerde = new SpecificAvroSerde<>();
        inputSerde.configure(serdeConfig, false);

        SpecificAvroSerde<AnomalyAlert> anomalySerde = new SpecificAvroSerde<>();
        anomalySerde.configure(serdeConfig, false);

        SpecificAvroSerde<ForecastAlert> forecastSerde = new SpecificAvroSerde<>();
        forecastSerde.configure(serdeConfig, false);

        inputTopic = testDriver.createInputTopic(
            EMAZscore.INPUT_TOPIC, 
            Serdes.String().serializer(), 
            inputSerde.serializer()
        );
        anomalyOutputTopic = testDriver.createOutputTopic(
            EMAZscore.ANOMALY_TOPIC, 
            Serdes.String().deserializer(), 
            anomalySerde.deserializer()
        );
        forecastOutputTopic = testDriver.createOutputTopic(
            EMAZscore.FORECAST_TOPIC, 
            Serdes.String().deserializer(), 
            forecastSerde.deserializer()
        );
    }

    @AfterEach
    public void tearDown() {
        if (testDriver != null) {
            testDriver.close(); 
        }
    }

    private PreProcessedSchema createEvent(String city, int aqi) {
        return PreProcessedSchema.newBuilder()
            .setTime(Instant.now())          
            .setCity(city)                   
            .setState("CA")                  
            .setPollutant("PM2.5")           
            .setAqi(aqi)                     
            .setConcentration(0.0)           
            .setCategory("Test Category")    
            .build();
    }

    @Test
    public void testAnomalyDetectionSpike() {
        String stationName = "Los Angeles - CA"; 
        
        for (int i = 0; i < 20; i++) {
            int aqi = 40 + ((i % 3) - 1) * 2; 
            inputTopic.pipeInput("dummy-key", createEvent("Los Angeles", aqi)); 
        }
        
        assertTrue(anomalyOutputTopic.isEmpty(), "No anomalies should be detected for normal data");
        
        inputTopic.pipeInput("dummy-key", createEvent("Los Angeles", 150)); 
        
        assertFalse(anomalyOutputTopic.isEmpty(), "An anomaly should be detected for the spike");

        KeyValue<String, AnomalyAlert> alertRecord = anomalyOutputTopic.readKeyValue(); 
        assertEquals(stationName, alertRecord.key); 
        assertEquals(150.0, alertRecord.value.getAqi()); 
        assertTrue(alertRecord.value.getZScore() > 3.0, "Z-score should be above threshold for the spike");
    }

    @Test 
    public void testForecastAlertTrend() {
        String stationName = "San Francisco - CA"; 

        for (int i = 0; i < 20; i++) {
            inputTopic.pipeInput("dummy-key", createEvent("San Francisco", 50));
        }

        inputTopic.pipeInput("dummy-key", createEvent("San Francisco", 70));
        inputTopic.pipeInput("dummy-key", createEvent("San Francisco", 85));
        inputTopic.pipeInput("dummy-key", createEvent("San Francisco", 95));
        inputTopic.pipeInput("dummy-key", createEvent("San Francisco", 98));
        inputTopic.pipeInput("dummy-key", createEvent("San Francisco", 99));
        
        assertFalse(forecastOutputTopic.isEmpty(), "A forecast alert should be detected for the increasing trend");

        KeyValue<String, ForecastAlert> forecastRecord = null;
        while (!forecastOutputTopic.isEmpty()) {
            forecastRecord = forecastOutputTopic.readKeyValue();
        }

        assertNotNull(forecastRecord);
        assertEquals(stationName, forecastRecord.key);
        assertTrue(forecastRecord.value.getCurrentAqi() < 100.0, "Current AQI should be below 100");
        assertTrue(forecastRecord.value.getPredictedAqi() >= 100.0, "Predicted AQI should exceed critical threshold");
        assertTrue(forecastRecord.value.getTrend() > 0, "Trend should be positive");
    }
}