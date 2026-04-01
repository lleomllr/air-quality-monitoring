package com.example.demo.controller;

import com.example.demo.config.security.JwtRequestFilter;
import com.example.demo.repository.aqi.AqiAlertRepository;
import com.example.demo.repository.aqi.CityAqiRepository;
import com.example.demo.repository.aqi.CityLocationRepository;
import com.example.demo.repository.client.UserRepository;
import com.example.demo.service.AqiService;
import com.example.demo.service.GlobalTopKService; // <- IMPORT AJOUTÉ
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AqiController.class, excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class AqiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AqiService aqiService;

    @MockitoBean
    private GlobalTopKService globalTopKService;

    @MockitoBean
    private AqiAlertRepository aqiAlertRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CityAqiRepository cityAqiRepository;

    @MockitoBean
    private CityLocationRepository cityLocationRepository;

    @MockitoBean
    private JwtRequestFilter jwtRequestFilter;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void getLatestCityGeoData_ShouldReturn200() throws Exception {
        // Arrange
        when(aqiService.getLatestCityGeoData()).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/aqi/cities/latest/geo"))
                .andExpect(status().isOk());
    }
}