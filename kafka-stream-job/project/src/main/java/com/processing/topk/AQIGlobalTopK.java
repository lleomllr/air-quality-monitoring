package com.processing.topk;

import com.type.avro.GlobalTopK;
import com.type.avro.StationAqiPair;
import com.type.avro.PreProcessedSchema;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;

public class AQIGlobalTopK {

    public static final String INPUT_TOPIC = "pre-processed-all";
    public static final String OUTPUT_TOPIC = "global-top10-aqi-events-v2";

    public static Topology buildTopology(Properties streamsConfig) {
        StreamsBuilder builder = new StreamsBuilder();

        Map<String, String> serdeConfig = Collections.singletonMap(
                "schema.registry.url", streamsConfig.getProperty("schema.registry.url")
        );

        SpecificAvroSerde<PreProcessedSchema> preProcessedSerde = new SpecificAvroSerde<>();
        preProcessedSerde.configure(serdeConfig, false);

        SpecificAvroSerde<StationAqiPair> pairSerde = new SpecificAvroSerde<>();
        pairSerde.configure(serdeConfig, false);

        SpecificAvroSerde<GlobalTopK> topKSerde = new SpecificAvroSerde<>();
        topKSerde.configure(serdeConfig, false);

        KStream<String, PreProcessedSchema> rawStream = builder.stream(
            INPUT_TOPIC, 
            Consumed.with(Serdes.String(), preProcessedSerde)
        );

        KStream<String, StationAqiPair> aqiStream = rawStream.map((key, value) -> {
            String stationID = value.getCity().toString() + " - " + value.getState().toString();
            Double aqi = (double) value.getAqi();
            String pollutant = value.getPollutant() != null ? value.getPollutant().toString() : "unknown";
            
            StationAqiPair stationData = StationAqiPair.newBuilder()
                .setStationId(stationID)
                .setAqi(aqi)
                .setPollutant(pollutant)
                .build(); 
            
            return KeyValue.pair(stationID, stationData);
        });

        //KTable pour la dernière valeur de chaque station
        KTable<String, StationAqiPair> latestAqiPerStation = aqiStream
            .toTable(Materialized.with(Serdes.String(), pairSerde));

        //Calcul du Top K global 
        KTable<String, GlobalTopK> globalTopKTable = latestAqiPerStation
            .groupBy(
                (stationID, stationData) -> KeyValue.pair(
                    "GLOBAL_TOP", 
                    stationData
                ),
                Grouped.with(Serdes.String(), pairSerde)
            )
            .aggregate(
                () -> GlobalTopK.newBuilder().setMaxK(10).setTopReadings(new HashMap<>()).build(),
                (key, newPair, aggregate) -> addAndSort(aggregate, newPair),                      
                (key, oldPair, aggregate) -> removeAndSort(aggregate, oldPair),                    
                Materialized.with(Serdes.String(), topKSerde)
            );

        globalTopKTable.toStream().to(
            OUTPUT_TOPIC, 
            Produced.with(Serdes.String(), topKSerde)
        );
        return builder.build();
    }

    private static GlobalTopK addAndSort(GlobalTopK aggregate, StationAqiPair newPair) {
        if (newPair == null) return aggregate;
        
        Map<CharSequence, StationAqiPair> readings = aggregate.getTopReadings();
        readings.put(newPair.getStationId(), newPair);
        
        keepOnlyTopK(readings, aggregate.getMaxK());
        aggregate.setTopReadings(readings);
        
        return aggregate;
    }

    private static GlobalTopK removeAndSort(GlobalTopK aggregate, StationAqiPair oldPair) {
        if (oldPair == null || oldPair.getStationId() == null) return aggregate;
        
        Map<CharSequence, StationAqiPair> readings = aggregate.getTopReadings();
        readings.remove(oldPair.getStationId());
        aggregate.setTopReadings(readings);
        
        return aggregate;
    }

    private static void keepOnlyTopK(Map<CharSequence, StationAqiPair> readings, int maxK) {
        if (readings.size() <= maxK) return;

        List<Map.Entry<CharSequence, StationAqiPair>> list = new ArrayList<>(readings.entrySet());
        list.sort((e1, e2) -> Double.compare(e2.getValue().getAqi(), e1.getValue().getAqi())); 

        readings.clear();
        for (int i = 0; i < maxK; i++) {
            readings.put(list.get(i).getKey(), list.get(i).getValue());
        }
    }

    public static void main(String[] args) {
        Properties props = new Properties(); 
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "aqi-global-topk-app-v4");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        props.put("schema.registry.url", "http://schema-registry:8081");

        KafkaStreams streams = new KafkaStreams(AQIGlobalTopK.buildTopology(props), props);
        System.out.println("Démarrage du job AQI Global TopK");
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