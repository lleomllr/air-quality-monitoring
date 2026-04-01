package com.example.demo;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Tests de base pour l'application Spring Boot.
 *
 * TEMPORAIREMENT DÉSACTIVÉ : Configuration multi-datasources complexe.
 * Les tests d'intégration nécessitent des conteneurs Docker actifs.
 *
 * Pour tester l'application :
 * 1. Lancer les conteneurs : docker compose up -d
 * 2. Lancer l'application : java -jar target/demo-0.0.1-SNAPSHOT.jar
 * 3. Tester les endpoints REST manuellement
 */
@SpringBootTest
@ActiveProfiles("test")
@Disabled("Multi-datasource configuration requires running Docker containers")
class DemoApplicationTests {

	@Test
	void contextLoads() {
		// Ce test vérifie que l'application démarre sans erreur
		// Désactivé car nécessite PostgreSQL + TimescaleDB actifs
	}

}


