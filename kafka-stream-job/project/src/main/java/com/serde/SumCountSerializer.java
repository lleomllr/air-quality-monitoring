package com.serde;

import org.apache.kafka.common.serialization.Serializer;
import java.nio.ByteBuffer;
import java.util.Map;

import com.type.SumCount;

public class SumCountSerializer implements Serializer<SumCount> {

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {}

    @Override
    public byte[] serialize(String topic, SumCount data) {
        if (data == null) return null;

        ByteBuffer byteBuffer = ByteBuffer.allocate(Double.BYTES + Integer.BYTES);
        byteBuffer.putDouble(data.getSum());
        byteBuffer.putInt(data.getCount());
        return byteBuffer.array();
    }

    @Override
    public void close() {}
}