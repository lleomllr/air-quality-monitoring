package com.example.demo.model.aqi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeStartStatePollutantId implements Serializable {
    private LocalDateTime timeStart;
    private String state;
    private String pollutant;
}
