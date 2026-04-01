package com.serde;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;

import com.type.SumCount;

public class SumCountSerde extends Serdes.WrapperSerde<SumCount> {
    public SumCountSerde() {
        super(new SumCountSerializer(), new SumCountDeserializer());
    }
}