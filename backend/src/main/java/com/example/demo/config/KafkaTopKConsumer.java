package com.example.demo.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.demo.dto.GlobalTopKEntryDTO;
import com.example.demo.service.GlobalTopKService;

@Component
public class KafkaTopKConsumer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaTopKConsumer.class);

    private final GlobalTopKService globalTopKService;

    public KafkaTopKConsumer(GlobalTopKService globalTopKService) {
        this.globalTopKService = globalTopKService;
    }

    @KafkaListener(topics = "${app.topk.topic}")
    public void consumeTopK(GenericRecord payload) {
        if (payload == null) {
            return;
        }

        Object topReadingsObject = payload.get("topReadings");
        if (!(topReadingsObject instanceof Map<?, ?> topReadings)) {
            return;
        }

        List<GlobalTopKEntryDTO> entries = new ArrayList<>();

        for (Map.Entry<?, ?> mapEntry : topReadings.entrySet()) {
            String stationId = mapEntry.getKey() != null ? mapEntry.getKey().toString() : "Unknown station";
            Object value = mapEntry.getValue();

            if (!(value instanceof GenericRecord stationRecord)) {
                continue;
            }

            String stationFromValue = stationRecord.get("stationId") != null
                    ? stationRecord.get("stationId").toString()
                    : stationId;

            double aqi = asDouble(stationRecord.get("aqi"));
            String pollutant = stationRecord.get("pollutant") != null
                    ? stationRecord.get("pollutant").toString()
                    : "unknown";

            entries.add(new GlobalTopKEntryDTO(stationFromValue, aqi, pollutant));
        }

        entries.sort(Comparator.comparingDouble(GlobalTopKEntryDTO::getAqi).reversed());
        globalTopKService.updateTopK(entries);
        logger.debug("TopK snapshot updated with {} stations", entries.size());
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value != null ? Double.parseDouble(value.toString()) : 0.0;
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }
}
