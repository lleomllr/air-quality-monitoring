package com.example.demo.model.aqi;

import org.locationtech.jts.geom.Point;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(schema = "aqi", name = "state_location")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StateLocation {

    @Id
    @Column(length = 200)
    private String state;

    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point geom;
}
