package com.example.demo.model.aqi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(schema = "aqi", name = "alerts")
@IdClass(TimeCityPollutantId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AqiAlert {

    @Id
    @Column(name = "time")
    private LocalDateTime time;

    @Column(nullable = false)
    private String state;

    @Id
    @Column(nullable = false)
    private String city;

    @Id
    @Column(nullable = false)
    private String pollutant;

    private Short aqi;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
