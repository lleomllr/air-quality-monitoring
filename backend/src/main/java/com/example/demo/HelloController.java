package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Ce contrôleur gère les requêtes HTTP de base.
 */
@RestController // Indique que cette classe est un contrôleur REST et que les méthodes retournent directement des données (pas des vues)
@RequestMapping("/api/test")
public class HelloController {

    /**
     * Gère les requêtes GET sur l'URL racine (http://localhost:8095/).
     *
     * @return Une chaîne de caractères de bienvenue.
     */
    @GetMapping("/")
    public String index() {
        return "Bonjour, Spring Boot 3 est bien installé et tourne dans Docker !";
    }

    /**
     * Gère les requêtes GET sur l'URL /hello.
     *
     * @return Une autre chaîne de caractères de bienvenue.
     */
    @GetMapping("/hello")
    public String hello() {
        return "Ceci est le chemin /hello.";
    }

    @GetMapping("/status")
    public String getStatus() {
        return "Backend est en ligne et l'API fonctionne!";
    }
}