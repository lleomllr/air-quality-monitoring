package com.processing.max;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;

import com.type.avro.MaxCitySchema;
import com.type.avro.MaxStateSchema;
import com.type.avro.PreProcessedSchema;

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

public class PM10MAX {
    private static final String DEFAULT_SCHEMA_REGISTRY_URL = "http://schema-registry:8081";

    public static Topology buildTopology() {
        return buildTopology(DEFAULT_SCHEMA_REGISTRY_URL);
    }

    public static Topology buildTopology(String schemaRegistryUrl) {
        SpecificAvroSerde<PreProcessedSchema> avroSerdePreProcessed = new SpecificAvroSerde<>();
        avroSerdePreProcessed.configure(Collections.singletonMap("schema.registry.url", schemaRegistryUrl), false);

        SpecificAvroSerde<MaxCitySchema> avroSerdeMaxCity = new SpecificAvroSerde<>();
        avroSerdeMaxCity.configure(Collections.singletonMap("schema.registry.url", schemaRegistryUrl), false);

        SpecificAvroSerde<MaxStateSchema> avroSerdeMaxState = new SpecificAvroSerde<>();
        avroSerdeMaxState.configure(Collections.singletonMap("schema.registry.url", schemaRegistryUrl), false);

        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, PreProcessedSchema> stream = builder.stream("pre-processed-pm10", Consumed.with(Serdes.String(), avroSerdePreProcessed));

        //Définition de la fenêtre glissante --> 1 heure toutes les 15 minutes
        Duration windowSize = Duration.ofHours(1);
        Duration advanceSize = Duration.ofHours(1);
        TimeWindows hoppingWindow = TimeWindows.ofSizeAndGrace(windowSize, Duration.ofMinutes(1)).advanceBy(advanceSize);

        //Recherche du max de l'AQI par ville
        KTable<Windowed<String>, Integer> maxAQIByCity = stream
                .groupBy((key, value) -> {
                    return value.getCity().toString() + "|||" + value.getState().toString();
                }, Grouped.with(Serdes.String(), avroSerdePreProcessed))
                .windowedBy(hoppingWindow)
                .aggregate(
                        () -> 0,
                        (key, value, max) -> {
                            return Math.max(max, value.getAqi());
                        },
                        Materialized.with(Serdes.String(), Serdes.Integer())
                )
                .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()));

        //Recherche du max de l'AQI par état
        KTable<Windowed<String>, Integer> maxAQIByState = stream
                .groupBy((key, value) -> {
                    return value.getState().toString();
                }, Grouped.with(Serdes.String(), avroSerdePreProcessed))
                .windowedBy(hoppingWindow)
                .aggregate(
                        () -> 0,
                        (key, value, max) -> {
                            return Math.max(max, value.getAqi());
                        },
                        Materialized.with(Serdes.String(), Serdes.Integer())
                )
                .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()));

        /**
         * Envoi des résultats vers le topic avg-(city|state)-hour
         */
        maxAQIByCity.toStream()
                //.peek((k, v) -> System.out.println("CITY MAX: " + k.key() + " => " + v))
                .map((windowedKey, max) -> {
                    String[] parts = windowedKey.key().split("\\|\\|\\|", 2);
                    String city = parts[0];
                    String state = parts[1];

                    MaxCitySchema avroMaxCity = MaxCitySchema.newBuilder()
                            .setCity(city)
                            .setState(state)
                            .setPollutant("PM10")
                            .setAqi(max)
                            .setTimeStart(Instant.ofEpochMilli(windowedKey.window().start()))
                            .setTimeEnd(Instant.ofEpochMilli(windowedKey.window().end()))
                            .build();

                    return KeyValue.pair(windowedKey.key(), avroMaxCity);
                })
                .to("max-city-hour", Produced.with(Serdes.String(), avroSerdeMaxCity));

        maxAQIByState.toStream()
                //.peek((k, v) -> System.out.println("STATE MAX: " + k.key() + " => " + v))
                .map((windowedKey, max) -> {

                    MaxStateSchema avroMaxState = MaxStateSchema.newBuilder()
                            .setState(windowedKey.key())
                            .setPollutant("PM10")
                            .setAqi(max)
                            .setTimeStart(Instant.ofEpochMilli(windowedKey.window().start()))
                            .setTimeEnd(Instant.ofEpochMilli(windowedKey.window().end()))
                            .build();

                    return KeyValue.pair(windowedKey.key(), avroMaxState);
                })
                .to("max-state-hour", Produced.with(Serdes.String(), avroSerdeMaxState));

        return builder.build();
    }

    public static void main(String args[]) {
        Properties props = new Properties();
        props.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        props.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "pm10-max-app");
        props.setProperty(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.setProperty(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class.getName());
        props.put("schema.registry.url", DEFAULT_SCHEMA_REGISTRY_URL);

        KafkaStreams streams = new KafkaStreams(PM10MAX.buildTopology(props.getProperty("schema.registry.url")), props);
        streams.start();

        //Permet au thread principal de rester actif
        final CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            streams.close();
            latch.countDown();
        }));

        try {
            latch.await(); //bloque le thread principal jusqu'à l'arrêt
        } catch (InterruptedException e) {
            streams.close();
            Thread.currentThread().interrupt();
        }
    }
}