package com.example.demo.controller;

import com.example.demo.model.aqi.AqiAlert;
import com.example.demo.repository.aqi.AqiAlertRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST Controller for alert endpoints from Kafka Sink (AqiAlert table)
 */
@RestController
@RequestMapping("/api/alerts")
@CrossOrigin(origins = "http://localhost:5173")
public class AlertController {

    @Autowired
    private AqiAlertRepository alertRepository;

    /**
     * Get recent alerts (last N hours)
     */
    @GetMapping
    public ResponseEntity<List<AqiAlert>> getRecentAlerts(
            @RequestParam(defaultValue = "168") int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return ResponseEntity.ok(alertRepository.findByTimeAfter(since));
    }

    /**
     * Get all alerts with pagination
     */
    @GetMapping("/all")
    public ResponseEntity<Page<AqiAlert>> getAllAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(alertRepository.findAll(PageRequest.of(page, size)));
    }

    /**
     * Get alerts by city
     */
    @GetMapping("/city/{city}")
    public ResponseEntity<List<AqiAlert>> getAlertsByCity(@PathVariable String city) {
        return ResponseEntity.ok(alertRepository.findByCity(city));
    }

    /**
     * Get alerts by state
     */
    @GetMapping("/state/{state}")
    public ResponseEntity<List<AqiAlert>> getAlertsByState(@PathVariable String state) {
        return ResponseEntity.ok(alertRepository.findByState(state));
    }
}

