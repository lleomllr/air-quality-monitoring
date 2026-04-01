package com.example.demo.service;

import com.example.demo.config.security.JwtUtil;
import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.model.client.User;
import com.example.demo.repository.client.UserRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    // Factory JTS pour créer des objets géométriques (SRID 4326 pour WGS84)
    private final GeometryFactory geometryFactory = new GeometryFactory();

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Cet email est déjà utilisé.");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());

        // Logique de conversion Latitude/Longitude -> Point PostGIS
        if (request.getLatitude() != null && request.getLongitude() != null) {
            // Dans PostGIS, l'ordre est généralement Longitude (X), Latitude (Y)
            Point userLocation = geometryFactory.createPoint(new Coordinate(request.getLongitude(), request.getLatitude()));
            userLocation.setSRID(4326); // On définit le système de coordonnées spatiales
            user.setLocation(userLocation);
        } else {
            user.setLocation(null);
        }

        // L'utilisateur est actif par défaut lors de sa création (défini dans l'entité User)
        User savedUser = userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(savedUser.getEmail());
        String token = jwtUtil.generateToken(userDetails);

        // Utilisation du constructeur à 3 arguments
        return new AuthResponse(token, savedUser.getEmail(), savedUser.getId());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtUtil.generateToken(userDetails);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        user.setActive(true); // Le client devient en ligne
        userRepository.save(user);

        // Utilisation du constructeur à 3 arguments
        return new AuthResponse(token, user.getEmail(), user.getId());
    }

    public void logoutUser(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setActive(false); // Le client passe hors ligne
            userRepository.save(user);
        });
    }
}