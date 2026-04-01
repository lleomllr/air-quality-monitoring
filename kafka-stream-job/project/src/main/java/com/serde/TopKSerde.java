package com.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map; 

public class TopKSerde<T> implements Serde<T> {
    private final ObjectMapper objectMapper = new ObjectMapper(); 
    private final Class<T> targetClass; 

    public TopKSerde(Class<T> targetClass) {
        this.targetClass = targetClass; 
    }

    @Override
    public Serializer<T> serializer() {
        return (topic, data) -> {
            if (data == null) return null; 
            try {
                return objectMapper.writeValueAsBytes(data); 
            } catch (Exception e) {
                throw new SerializationException("Error serializing JSON message", e);
            }
        }; 
    }
    
    @Override
    public Deserializer<T> deserializer() {
        return (topic, data) -> {
            if (data == null) return null; 
            try {
                return objectMapper.readValue(data, targetClass); 
            } catch (Exception e) {
                throw new SerializationException("Error deserializing JSON message", e);
            }
        }; 
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {}

    @Override
    public void close() {}
}