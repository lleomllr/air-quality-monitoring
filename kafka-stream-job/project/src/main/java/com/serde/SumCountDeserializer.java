package com.serde;

import org.apache.kafka.common.serialization.Deserializer;
import java.nio.ByteBuffer;
import java.util.Map;

import com.type.SumCount;

public class SumCountDeserializer implements Deserializer<SumCount> {

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {}

    @Override
    public SumCount deserialize(String topic, byte[] data) {
        if (data == null || data.length == 0) return new SumCount(0.0, 0);

        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        double sum = byteBuffer.getDouble();
        int count = byteBuffer.getInt();
        return new SumCount(sum, count);
    }

    @Override
    public void close() {}
}