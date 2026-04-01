package com.example.demo.model.aqi;

import org.locationtech.jts.geom.Point;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "aqi", name = "city_location")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CityLocation {

    @Id
    @Column(length = 200)
    private String city;

    private Double longitude;

    private Double latitude;

    @Column(length = 50)
    private String state;

    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point geom;
}
