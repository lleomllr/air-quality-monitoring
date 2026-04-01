package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String email;
    private UUID userId;
    private String message;

    // Constructeur pour le succès (3 arguments)
    public AuthResponse(String token, String email, UUID userId) {
        this.token = token;
        this.email = email;
        this.userId = userId;
    }

    // Constructeur pour l'erreur (1 argument)
    public AuthResponse(String message) {
        this.message = message;
    }
}