package com.alerting;

import com.type.avro.PreProcessedSchema;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.processor.api.ContextualProcessor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.KeyValue;

import org.apache.kafka.common.serialization.Serdes;

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import java.time.Duration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

public class AlertConsumer {

    private static final String ALERTS_TOPIC = "alerts";
    private static final String TIMESCALEDB_URL = "jdbc:postgresql://timescaledb:5432/db_airquality?user=admin&password=admin";
    private static final String POSTGRES_URL = "jdbc:postgresql://postgres:5432/clients?user=admin&password=admin";

    private static final String SMTP_HOST = "smtp-relay.brevo.com";
    private static final String SMTP_PORT = "587";
    private static final String BREVO_LOGIN = "a3a45d001@smtp-brevo.com";
    private static final String BREVO_PWD = "Jy3djXU6QbwrVBTZ";
    private static final String FROM_EMAIL = "airnowalerts@gmail.com"; 
    private static final String FROM_NAME = "Alertes AQI";

    /**
     * Construction de la topologie Kafka Streams pour consommer les alertes, éviter les doublons, sauvegarder dans la DB et envoyer les emails
     */
    public static Topology buildTopology() {

        StreamsBuilder builder = new StreamsBuilder();

        SpecificAvroSerde<PreProcessedSchema> avroSerde = new SpecificAvroSerde<>();
        avroSerde.configure(Collections.singletonMap("schema.registry.url", "http://schema-registry:8081"), false);

        StoreBuilder<KeyValueStore<String, Long>> storeBuilder =
            Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore("sent-alerts-store"),
                Serdes.String(),
                Serdes.Long()
            );
        builder.addStateStore(storeBuilder);

        KStream<String, PreProcessedSchema> alerts = builder.stream(ALERTS_TOPIC,
            Consumed.with(Serdes.String(), avroSerde));

        alerts.process(() -> new ContextualProcessor<String, PreProcessedSchema, Void, Void>() {

            private KeyValueStore<String, Long> store;
            private static final long TTL_MS = 3_600_000L; // 1 heure avant de reconsidérer l'alerte

            @Override
            public void init(ProcessorContext<Void, Void> context) {
                super.init(context);
                this.store = context.getStateStore("sent-alerts-store");

                context.schedule(Duration.ofMinutes(30), PunctuationType.WALL_CLOCK_TIME, timestamp -> {
                    try (KeyValueIterator<String, Long> it = store.all()) {
                        while (it.hasNext()) {
                            KeyValue<String, Long> entry = it.next();
                            if (timestamp - entry.value > TTL_MS) {
                                store.delete(entry.key);
                            }
                        }
                    }
                });
            }

            /**
             * Pour chaque alerte reçue, on génère une clé de déduplication basée sur la ville, l'état, le polluant et la catégorie.
             * Si une alerte similaire a déjà été envoyée récemment (dans les dernières 24h), on ignore cette alerte.
             * Sinon, on enregistre l'alerte dans la base de données et on envoie des emails aux clients concernés.
             */
            @Override
            public void process(Record<String, PreProcessedSchema> record) {

                String dedupeKey = record.value().getCity() + "-" + record.value().getState() + "-" + record.value().getPollutant() + "-" + record.value().getCategory();

                Long lastSent = store.get(dedupeKey);
                long now = System.currentTimeMillis();

                if (lastSent == null || (now - lastSent) > TTL_MS) {
                    store.put(dedupeKey, now);
                    try {
                        PreProcessedSchema alert = record.value();
                        saveAlertToDatabase(alert);
                        List<String> emails = getEmailsWithinRadius(
                            alert.getCity().toString(),
                            alert.getState().toString(),
                            50000.0
                        );

                        if (!emails.isEmpty()) {
                            sendEmails(emails, alert);
                        } else {
                            // Pour tester, rentrer le mail auquel l'alerte sera envoyée

                            //List<String> emailsTest = new ArrayList<>();
                            //emailsTest.add("test@test.com");
                            //sendEmails(emailsTest, alert);
                            System.out.println("pas de clients dans la zone.");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("Alerte déjà envoyée, ignorée : " + dedupeKey);
                }
            }

        }, "sent-alerts-store");

        return builder.build();
    }

    /**
     * Récupération des emails des clients actifs dans un rayon de 50km autour de la ville concernée par l'alerte
     * @param city
     * @param state
     * @param radiusMeters
     * @return
     * @throws Exception
     */
    private static List<String> getEmailsWithinRadius(String city, String state, double radiusMeters) throws Exception {
        
        List<String> emails = new ArrayList<>();

        String cityQuery = """
            SELECT longitude, latitude
            FROM aqi.city_location
            WHERE city = ? AND state = ?
            LIMIT 1
        """;

        double lon = 0.0;
        double lat = 0.0;
        boolean found = false;

        try (Connection conn = DriverManager.getConnection(TIMESCALEDB_URL);
            PreparedStatement stmt = conn.prepareStatement(cityQuery)) {

            stmt.setString(1, city);
            stmt.setString(2, state);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                lon = rs.getDouble("longitude");
                lat = rs.getDouble("latitude");
                found = true;
                System.out.println("Ville/état trouvés : " + city + "/" + state);
            }
        }

        if (!found) {
            System.out.println("Ville/état non trouvé : " + city + "/" + state);
            return emails;
        }

        String clientsQuery = """
            SELECT email 
            FROM clients 
            WHERE active = true
            AND ST_DWithin(
                    location::geography, 
                    ST_MakePoint(?, ?)::geography, 
                    ?
                )
        """;

        try (Connection conn = DriverManager.getConnection(POSTGRES_URL);
            PreparedStatement stmt = conn.prepareStatement(clientsQuery)) {

            stmt.setDouble(1, lon);   
            stmt.setDouble(2, lat); 
            stmt.setDouble(3, radiusMeters);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                emails.add(rs.getString("email"));
            }
        }

        return emails;
    }

    /**
     * Envoi d'emails via le SMTP de Brevo (Sendinblue) aux clients concernés par l'alerte
     * @param emails
     * @param alert
     */
    private static void sendEmails(List<String> emails, PreProcessedSchema alert) {

        Properties propsSMTP = new Properties();
        propsSMTP.put("mail.smtp.host", SMTP_HOST);
        propsSMTP.put("mail.smtp.port", SMTP_PORT);
        propsSMTP.put("mail.smtp.auth", "true");
        propsSMTP.put("mail.smtp.starttls.enable", "true");
        propsSMTP.put("mail.smtp.ssl.protocols", "TLSv1.2"); 

        Session session = Session.getInstance(propsSMTP, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(BREVO_LOGIN, BREVO_PWD);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(FROM_EMAIL, FROM_NAME));

            for (String email : emails) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(email));
            }

            message.setSubject("Alerte sur la qualité de l'air dans votre région");
            message.setText("Bonjour,\n La concentration du polluant " 
                + alert.getPollutant() 
                + " a été détectée comme "
                + alert.getCategory()
                + " à " 
                + alert.getCity() + ", " + alert.getState() 
                + ". Pour plus d'informations veuillez consulter le site."
            , "UTF-8"); 

            Transport.send(message);
            System.out.println("Email envoyé via Brevo SMTP à " + emails.size() + " destinataires");
        } catch (MessagingException e) {
            System.err.println("Erreur lors de l'envoi email via SMTP : " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Erreur inattendue lors de l'envoi : " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sauvegarde de l'alerte dans la base de données TimescaleDB pour le Dashboard
     * @param alert
     */
    private static void saveAlertToDatabase(PreProcessedSchema alert) {
        String insertQuery = "INSERT INTO aqi.alerts (time, state, city, pollutant, aqi, created_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW())";
        try (Connection conn = DriverManager.getConnection(TIMESCALEDB_URL);
             PreparedStatement stmt = conn.prepareStatement(insertQuery)) {

            stmt.setTimestamp(1, Timestamp.from(alert.getTime()));
            stmt.setString(2, String.valueOf(alert.getState()));
            stmt.setString(3, String.valueOf(alert.getCity()));
            stmt.setString(4, String.valueOf(alert.getPollutant()));
            stmt.setInt(5, alert.getAqi());

            stmt.executeUpdate();
            System.out.println("Alerte enregistrée dans la DB pour le Dashboard : " + alert.getCity());

        } catch (Exception e) {
            // On ignore l'erreur si la clé primaire existe déjà (l'alerte est déjà enregistrée)
            if (!e.getMessage().contains("duplicate key value")) {
                System.err.println("Erreur lors de la sauvegarde DB : " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        Properties props = new Properties();
		props.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
		props.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "alert-app");
		props.setProperty(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
		props.setProperty(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class.getName());
        props.put("schema.registry.url", "http://schema-registry:8081");

        KafkaStreams streams = new KafkaStreams(buildTopology(), props);
        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }
}