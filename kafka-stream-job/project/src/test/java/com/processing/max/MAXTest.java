package com.processing.max;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.type.avro.MaxCitySchema;
import com.type.avro.MaxStateSchema;
import com.type.avro.PreProcessedSchema;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroDeserializer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

class MAXTest {
    private TopologyTestDriver testDriver;
    private static final String SCHEMA_REGISTRY_SCOPE = "mock://max-test";

    private TestInputTopic<String, byte[]> inputTopic;
    private TestOutputTopic<String, MaxCitySchema> outputCityTopic;
    private TestOutputTopic<String, MaxStateSchema> outputStateTopic;
    private KafkaAvroSerializer inputSerializer;

    @BeforeEach
    void setup() {
        Topology topology = OzoneMAX.buildTopology(SCHEMA_REGISTRY_SCOPE);

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-max-"+UUID.randomUUID());
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class.getName());
        props.put("schema.registry.url", SCHEMA_REGISTRY_SCOPE);

        this.inputSerializer = new KafkaAvroSerializer();
        this.inputSerializer.configure(Collections.singletonMap("schema.registry.url", SCHEMA_REGISTRY_SCOPE), false);

        SpecificAvroDeserializer<MaxCitySchema> maxCityDeserializer = new SpecificAvroDeserializer<>();
        maxCityDeserializer.configure(Collections.singletonMap("schema.registry.url", SCHEMA_REGISTRY_SCOPE), false);

        SpecificAvroDeserializer<MaxStateSchema> maxStateDeserializer = new SpecificAvroDeserializer<>();
        maxStateDeserializer.configure(Collections.singletonMap("schema.registry.url", SCHEMA_REGISTRY_SCOPE), false);

        this.testDriver = new TopologyTestDriver(topology, props);

        this.inputTopic = this.testDriver.createInputTopic("pre-processed-ozone", Serdes.String().serializer(), Serdes.ByteArray().serializer());
        this.outputCityTopic = this.testDriver.createOutputTopic("max-city-hour", Serdes.String().deserializer(), maxCityDeserializer);
        this.outputStateTopic = this.testDriver.createOutputTopic("max-state-hour", Serdes.String().deserializer(), maxStateDeserializer);
    }

    @Test
    void testTopologyProcess() {
        PreProcessedSchema[] mockContent =  {
                buildInput("Narragansett", "RI", 32, 0.03456),
                buildInput("Narragansett", "RI", 33, 0.03456),
                buildInput("West Greenwich", "RI", 31, 0.03024)
        };

        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");
        int sec = 0;

        for(PreProcessedSchema content : mockContent) {
            this.inputTopic.pipeInput("key", serializeInput(content), t0.plusSeconds(sec));
            sec += 60;
        }
        this.inputTopic.pipeInput("late", serializeInput(mockContent[0]), t0.plus(Duration.ofMinutes(71)));

        List<KeyValue<String, MaxCitySchema>> cityResults = this.outputCityTopic.readKeyValuesToList();
        List<KeyValue<String, MaxStateSchema>> stateResults = this.outputStateTopic.readKeyValuesToList();

        assertEquals(2, cityResults.size());
        assertTrue(stateResults.size() >= 1);

        cityResults.forEach(r -> assertEquals("O3", r.value.getPollutant().toString()));
        stateResults.forEach(r -> assertEquals("O3", r.value.getPollutant().toString()));
    }

    @Test
    void testComputeAVG() {
        PreProcessedSchema[] mockContent =  {
                buildInput("Narragansett", "RI", 32, 0.03456),
                buildInput("Narragansett", "RI", 33, 0.03456),
                buildInput("West Greenwich", "RI", 31, 0.03024)
        };

        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");
        int sec = 0;

        for(PreProcessedSchema content : mockContent) {
            this.inputTopic.pipeInput("key", serializeInput(content), t0.plusSeconds(sec));
            sec += 60;
        }
        this.inputTopic.pipeInput("late", serializeInput(mockContent[0]), t0.plus(Duration.ofMinutes(71)));

        List<KeyValue<String, MaxCitySchema>> cityResults = this.outputCityTopic.readKeyValuesToList();
        assertEquals(2, cityResults.size());

        Optional<MaxCitySchema> city1 = cityResults
                .stream()
                .map(r -> r.value)
                .filter(city -> city.getCity().toString().equals("Narragansett"))
                .findFirst();
        assertTrue(city1.isPresent(), "Narragansett not found");
        int maxCity1 = city1.get().getAqi();
        assertEquals(33, maxCity1);

        Optional<MaxCitySchema> city2 = cityResults
                .stream()
                .map(r -> r.value)
                .filter(city -> city.getCity().toString().equals("West Greenwich"))
                .findFirst();
        assertTrue(city2.isPresent(), "West Greenwich not found");
        int maxCity2 = city2.get().getAqi();
        assertEquals(31, maxCity2);

        List<KeyValue<String, MaxStateSchema>> stateResult = this.outputStateTopic.readKeyValuesToList();
        assertTrue(stateResult.size() >= 1);

        Optional<MaxStateSchema> state = stateResult
                .stream()
                .map(r -> r.value)
                .filter(s -> s.getState().toString().equals("RI") && s.getAqi() == 33)
                .findFirst();
        assertTrue(state.isPresent(), "State RI with AQI 33 not found");
    }

    private PreProcessedSchema buildInput(String city, String state, int aqi, double concentration) {
        return PreProcessedSchema.newBuilder()
                .setTime(Instant.parse("2025-01-01T00:00:00Z"))
                .setCity(city)
                .setState(state)
                .setPollutant("O3")
                .setAqi(aqi)
                .setConcentration(concentration)
                .setCategory("Good")
                .build();
    }

    private byte[] serializeInput(PreProcessedSchema input) {
        return this.inputSerializer.serialize("pre-processed-ozone", input);
    }

    @AfterEach
    void tearDown() {
        this.testDriver.close();
    }
}