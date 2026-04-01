package com.example.demo.model.client;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * User entity for client database.
 * Represents a user in the system with authentication and profile information.
 * - id (UUID)
 * - email
 * - fullName
 * - location (Point, SRID 4326)
 * - active
 * - lastUpdated
 */
@Entity
@Table(name = "clients")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid default gen_random_uuid()", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 255)
    private String fullName;

    @Column(nullable = false)
    private String password;

    @Column(columnDefinition = "geometry(Point,4326)")
    private org.locationtech.jts.geom.Point location;

    @Column(nullable = false)
    private boolean active = true;

    @UpdateTimestamp
    @Column(name = "last_updated", columnDefinition = "timestamptz default now()")
    private OffsetDateTime lastUpdated;
}
