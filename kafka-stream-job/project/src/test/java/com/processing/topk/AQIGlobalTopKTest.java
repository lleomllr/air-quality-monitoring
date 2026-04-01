package com.processing.topk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.type.avro.GlobalTopK;
import com.type.avro.PreProcessedSchema;
import com.type.avro.StationAqiPair;

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

public class AQIGlobalTopKTest {

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, PreProcessedSchema> inputTopic;
    private TestOutputTopic<String, GlobalTopK> outputTopic;
    private SpecificAvroSerde<PreProcessedSchema> inputSerde;
    private SpecificAvroSerde<GlobalTopK> topKSerde;

    private static final String MOCK_SCHEMA_REGISTRY_URL = "mock://test-registry";

    @BeforeEach
    public void setup() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-topk-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put("schema.registry.url", MOCK_SCHEMA_REGISTRY_URL);

        Topology topology = AQIGlobalTopK.buildTopology(props);

        testDriver = new TopologyTestDriver(topology, props);

        Map<String, String> serdeConfig = Collections.singletonMap("schema.registry.url", MOCK_SCHEMA_REGISTRY_URL);
        
        inputSerde = new SpecificAvroSerde<>();
        inputSerde.configure(serdeConfig, false);

        topKSerde = new SpecificAvroSerde<>();
        topKSerde.configure(serdeConfig, false);

        inputTopic = testDriver.createInputTopic(
                AQIGlobalTopK.INPUT_TOPIC,
                Serdes.String().serializer(),
                inputSerde.serializer()
        );

        outputTopic = testDriver.createOutputTopic(
                AQIGlobalTopK.OUTPUT_TOPIC,
                Serdes.String().deserializer(),
                topKSerde.deserializer()
        );
    }

    @AfterEach
    public void tearDown() {
        if (testDriver != null) {
            testDriver.close();
        }
        if (inputSerde != null) {
            inputSerde.close();
        }
        if (topKSerde != null) {
            topKSerde.close();
        }
    }

    private PreProcessedSchema createDummyEvent(String city, int aqi) {
        return PreProcessedSchema.newBuilder()
            .setTime(Instant.now())          
            .setCity(city)                   
            .setState("State")               
            .setPollutant("O3")              
            .setAqi(aqi)                     
            .setConcentration(0.0)           
            .setCategory("Test Category")    
            .build();
    }

    @Test
    public void testTopKCalculationAndEviction() {
        for (int i = 1; i <= 10; i++) {
            inputTopic.pipeInput("City_" + i + " - State", createDummyEvent("City_" + i, i * 10));
        }

        GlobalTopK currentTopK = null;
        while (!outputTopic.isEmpty()) {
            currentTopK = outputTopic.readKeyValue().value;
        }

        assertNotNull(currentTopK);
        assertEquals(10, currentTopK.getTopReadings().size());
        assertTrue(currentTopK.getTopReadings().containsKey("City_1 - State"));
        assertTrue(currentTopK.getTopReadings().containsKey("City_10 - State"));

        inputTopic.pipeInput("City_11_CRITIQUE - State", createDummyEvent("City_11_CRITIQUE", 999));

        while (!outputTopic.isEmpty()) {
            currentTopK = outputTopic.readKeyValue().value;
        }

        Map<CharSequence, StationAqiPair> readings = currentTopK.getTopReadings();
        
        assertEquals(10, readings.size());
        assertTrue(readings.containsKey("City_11_CRITIQUE - State"), "City_11 devrait être dans le Top");
        assertEquals(999.0, readings.get("City_11_CRITIQUE - State").getAqi());
        assertFalse(readings.containsKey("City_1 - State"), "City_1 aurait dû être éjectée");
        assertTrue(readings.containsKey("City_2 - State"), "City_2 devrait toujours être là");
    }
}