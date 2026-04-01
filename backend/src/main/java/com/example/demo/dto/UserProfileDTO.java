package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for user profile information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    private UUID id;
    private String email;
    private String fullName;
    private String password;
    private String location; // WKT ou GeoJSON, à adapter selon usage
    private boolean active;
    private OffsetDateTime lastUpdated;
}
