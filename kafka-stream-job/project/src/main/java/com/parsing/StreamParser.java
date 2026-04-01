package com.parsing;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.*;
import org.json.JSONObject;
import org.json.JSONArray;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import com.type.avro.PreProcessedSchema;

import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.time.Instant;

public class StreamParser {
    public static Topology buildTopology() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> stream = builder.stream("rss-raw", Consumed.with(Serdes.String(), Serdes.String()));

        SpecificAvroSerde<PreProcessedSchema> avroSerde = new SpecificAvroSerde<>();
        avroSerde.configure(Collections.singletonMap("schema.registry.url", "http://schema-registry:8081"), false);

        KStream<String, JSONObject> parsedData = stream
                .flatMapValues(value -> {
                    try {
                        // On récupère le contenu RSS
                        JSONObject json = new JSONObject(value);
                        RSSParser parser = new RSSParser(json.getJSONObject("payload").getString("content"));

                        // On extrait et on enrichit les données
                        JSONObject parsedJson = parser.getExtractedData();
                        for (Object obj : parsedJson.getJSONArray("pollutants")) {
                            JSONObject pollutant = (JSONObject) obj;

                            int aqi = pollutant.getInt("aqi");
                            String name = pollutant.getString("name");

                            AQICalculator calculator = new AQICalculator(aqi, name);
                            double concentration = calculator.getConcentration();

                            pollutant.put("concentration", concentration);
                        }
                        // Trouver l'O3, PM2.5 ou PM10
                        List<JSONObject> result = new ArrayList<>();
                        for (Object obj : parsedJson.getJSONArray("pollutants")) {
                            JSONObject pollutant = (JSONObject) obj;
                            String name = pollutant.getString("name");
                            if (name.equals("O3") || name.equals("PM2.5") || name.equals("PM10")) {
                                JSONObject copy = new JSONObject(parsedJson.toString());
                                copy.remove("pollutants");
                                copy.put("pollutant", pollutant);
                                result.add(copy);
                            }
                        }
                        return result;
                    } catch (Exception e) {
                        // Si l'extraction initiale plante, on ignore l'enregistrement
                        System.err.println("Erreur lors de l'extraction initiale: " + e.getMessage());
                        return new ArrayList<>();
                    }
                });

        KStream<String, PreProcessedSchema> processedStream = parsedData
                .mapValues(result -> {
                    try {
                        // On utilise optString pour éviter une NullPointerException
                        String dateStr = result.optString("lastUpdate", "");

                        if (dateStr == null || dateStr.trim().isEmpty()) {
                            System.err.println("Avertissement : Date vide ou manquante ignorée.");
                            return null; // On renvoie null pour que le filtre l'élimine proprement
                        }

                        // Récupérer les polluants
                        return PreProcessedSchema.newBuilder()
                                .setTime(Instant.parse(dateStr))
                                .setCity(result.getString("city"))
                                .setState(result.getString("state"))
                                .setPollutant(result.getJSONObject("pollutant").getString("name"))
                                .setAqi(result.getJSONObject("pollutant").getInt("aqi"))
                                .setConcentration(result.getJSONObject("pollutant").getDouble("concentration"))
                                .setCategory(result.getJSONObject("pollutant").getString("category"))
                                .build();
                    } catch (Exception e) {
                        System.err.println("Erreur de parsing des données (probablement format de date): " + e.getMessage());
                        return null; // On renvoie null pour ne pas faire crasher l'app
                    }
                })
                // On ajoute "value != null" pour s'assurer que les erreurs filtrées ci-dessus n'aillent pas plus loin
                .filter((key, value) -> value != null && value.getAqi() > 0 && !value.getCity().equals(""));

        // Embranchement des flux selon le polluant
        KStream<String, PreProcessedSchema> o3Stream = processedStream.filter(
                (key, value) -> containsPollutant(value, "O3")
        );
        KStream<String, PreProcessedSchema> pm25Stream = processedStream.filter(
                (key, value) -> containsPollutant(value, "PM2.5")
        );
        KStream<String, PreProcessedSchema> pm10Stream = processedStream.filter(
                (key, value) -> containsPollutant(value, "PM10")
        );

        // Redirection vers le topic d'alerte si AQI élevé
        KStream<String, PreProcessedSchema> alerts = processedStream.filter(
                (key, value) -> hasHighAQI(value)
        );

        processedStream
                .to("pre-processed-all", Produced.with(Serdes.String(), avroSerde));

        // On envoie chaque flux vers son topic dédié
        o3Stream
                .to("pre-processed-ozone", Produced.with(Serdes.String(), avroSerde));

        pm25Stream
                .to("pre-processed-pm25", Produced.with(Serdes.String(), avroSerde));

        pm10Stream
                .to("pre-processed-pm10", Produced.with(Serdes.String(), avroSerde));

        alerts
                .to("alerts", Produced.with(Serdes.String(), avroSerde));

        return builder.build();
    }

    public static void main(String[] args) {
        Properties props = new Properties();
        props.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        props.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "rss-parser-app");
        props.setProperty(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.setProperty(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class.getName());
        props.put("schema.registry.url", "http://schema-registry:8081");

        KafkaStreams streams = new KafkaStreams(buildTopology(), props);
        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }

    //Méthode pour vérifier la présence d'un polluant spécifique
    private static boolean containsPollutant(PreProcessedSchema avro, String pollutantName) {
        return avro.getPollutant().equals(pollutantName);
    }

    private static boolean hasHighAQI(PreProcessedSchema avro) {
        return avro.getAqi() >= 100;
    }
}