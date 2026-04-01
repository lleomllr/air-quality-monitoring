package com.processing.avg;

import com.serde.SumCountSerde;
import com.type.SumCount;

import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.*;
import org.json.JSONObject;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import com.type.avro.PreProcessedSchema;
import com.type.avro.AvgCitySchema;
import com.type.avro.AvgStateSchema;

import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.Date;
import java.util.Collections;
import java.time.Instant;

public class PM25AVGDay {
    public static Topology buildTopology() {
        SpecificAvroSerde<PreProcessedSchema> avroSerdePreProcessed = new SpecificAvroSerde<>();
        avroSerdePreProcessed.configure(Collections.singletonMap("schema.registry.url", "http://schema-registry:8081"), false);

        SpecificAvroSerde<AvgCitySchema> avroSerdeAvgCity = new SpecificAvroSerde<>();
        avroSerdeAvgCity.configure(Collections.singletonMap("schema.registry.url", "http://schema-registry:8081"), false);

        SpecificAvroSerde<AvgStateSchema> avroSerdeAvgState = new SpecificAvroSerde<>();
        avroSerdeAvgState.configure(Collections.singletonMap("schema.registry.url", "http://schema-registry:8081"), false);

        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, PreProcessedSchema> stream = builder.stream("pre-processed-pm25", Consumed.with(Serdes.String(), avroSerdePreProcessed));

        //Définition de la fenêtre glissante --> 24 heures toutes les 8 heures
        Duration windowSize = Duration.ofHours(24);
        Duration advanceSize = Duration.ofHours(8);
        TimeWindows tumblingWindow = TimeWindows.of(windowSize).advanceBy(advanceSize).grace(Duration.ofMinutes(1));

        //Calcul de la moyenne de l'AQI par ville
        KTable<Windowed<String>, SumCount> avgAQIByCity = stream
                .groupBy((key, value) -> {
                    return value.getCity().toString() + "|||" + value.getState().toString();
                })
                .windowedBy(tumblingWindow)
                .aggregate(
                        () -> new SumCount(0.0, 0),
                        (key, value, agg) -> {
                            agg.add(value.getAqi());
                            return agg;
                        },
                        Materialized.with(Serdes.String(), new SumCountSerde())
                )
                .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()));


        //Calcul de la moyenne de l'AQI par état
        KTable<Windowed<String>, SumCount> avgAQIByState = stream
                .groupBy((key, value) -> {
                    return value.getState().toString();
                })
                .windowedBy(tumblingWindow)
                .aggregate(
                        () -> new SumCount(0.0, 0),
                        (key, value, agg) -> {
                            agg.add(value.getAqi());
                            return agg;
                        },
                        Materialized.with(Serdes.String(), new SumCountSerde())
                )
                .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()));


        /**
         * Envoi des résultats vers le topic avg-(city|state)-hour
         */
        avgAQIByCity.toStream()
                //.peek((k, v) -> System.out.println("CITY AVG: " + k.key() + " => " + v))
                .mapValues(sumCount -> sumCount.average())
                .map((windowedKey, avg) -> {
                    String[] parts = windowedKey.key().split("\\|\\|\\|", 2);
                    String city = parts[0];
                    String state = parts[1];

                    AvgCitySchema avroAvgCity = AvgCitySchema.newBuilder()
                            .setCity(city)
                            .setState(state)
                            .setPollutant("PM25")
                            .setAqi(avg)
                            .setTimeStart(Instant.ofEpochMilli(windowedKey.window().start()))
                            .setTimeEnd(Instant.ofEpochMilli(windowedKey.window().end()))
                            .build();

                    return KeyValue.pair(windowedKey.key(), avroAvgCity);
                })
                .to("avg-city-day", Produced.with(Serdes.String(), avroSerdeAvgCity));

        avgAQIByState.toStream()
                //.peek((k, v) -> System.out.println("STATE AVG: " + k.key() + " => " + v))
                .mapValues(sumCount -> sumCount.average())
                .map((windowedKey, avg) -> {

                    AvgStateSchema avroAvgState = AvgStateSchema.newBuilder()
                            .setState(windowedKey.key())
                            .setPollutant("PM25")
                            .setAqi(avg)
                            .setTimeStart(Instant.ofEpochMilli(windowedKey.window().start()))
                            .setTimeEnd(Instant.ofEpochMilli(windowedKey.window().end()))
                            .build();

                    return KeyValue.pair(windowedKey.key(), avroAvgState);
                })
                .to("avg-state-day", Produced.with(Serdes.String(), avroSerdeAvgState));

        return builder.build();
    }

    public static void main(String args[]) {
        Properties props = new Properties();
        props.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        props.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "pm25-avg-app");
        props.setProperty(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.setProperty(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class.getName());
        props.put("schema.registry.url", "http://schema-registry:8081");

        KafkaStreams streams = new KafkaStreams(PM25AVGDay.buildTopology(), props);
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