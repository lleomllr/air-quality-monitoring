package com.example.demo.service;

import com.example.demo.dto.CityGeoMeasurementDTO;
import com.example.demo.repository.aqi.CityAqiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AqiServiceTest {

    @Mock
    private CityAqiRepository cityAqiRepository;

    @InjectMocks
    private AqiService aqiService;

    @Test
    void getLatestCityGeoData_ShouldReturnListOfData() {
        // Arrange : On dit au Mock de renvoyer une liste vide quand on l'appelle
        when(cityAqiRepository.findLatestWithCoordinates()).thenReturn(List.of());

        // Act : On appelle la vraie méthode du service
        List<CityGeoMeasurementDTO> result = aqiService.getLatestCityGeoData();

        // Assert : On vérifie que le résultat n'est pas nul et que le repository a bien été appelé 1 fois
        assertNotNull(result);
        assertEquals(0, result.size());
        verify(cityAqiRepository, times(1)).findLatestWithCoordinates();
    }
}