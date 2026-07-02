package com.bolsanueva;

import com.bolsanueva.model.EnvaseFisico;
import com.bolsanueva.model.TrazabilidadLotes;
import com.bolsanueva.model.HistorialUsos;
import com.bolsanueva.repository.EnvaseFisicoRepository;
import com.bolsanueva.repository.TrazabilidadLotesRepository;
import com.bolsanueva.repository.HistorialUsosRepository;
import com.bolsanueva.service.EvaluadorScrapService;
import com.bolsanueva.exception.ValidacionScrapException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BANCO DE PRUEBAS UNITARIAS DE CONTROL DE CALIDAD Y LOGÍSTICA (SGC)
 * @version 6.1 (Sincronización de Excepciones)
 */
@ExtendWith(MockitoExtension.class)
class AppTest {

    @Mock
    private EnvaseFisicoRepository envaseRepository;

    @Mock
    private TrazabilidadLotesRepository loteRepository;

    @Mock
    private HistorialUsosRepository historialRepository;

    @InjectMocks
    private EvaluadorScrapService evaluadorService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ------------------------------------------------------------------------
    // PRUEBA 1: BOTÓN NUEVO ENVASE E IMPRESIÓN ASOCIADA
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Debería registrar e imprimir un nuevo envase autocalculando su correlativo")
    void testRegistrarEImprimirNuevoEnvase() throws Exception { // <-- AGREGADO AQUÍ
        when(envaseRepository.findMaxCorrelativoByTipoEnvase("ENV-01")).thenReturn(Optional.of(42));
        when(envaseRepository.save(any(EnvaseFisico.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EnvaseFisico resultado = evaluadorService.registrarEImprimirNuevoEnvase("ENV-01");

        assertNotNull(resultado);
        assertEquals("ENV-01-0043", resultado.getIdBolson());
        assertEquals("DISPONIBLE", resultado.getEstado());
        assertEquals("REC_DEPOSITO", resultado.getUbicacionActual());
        
        verify(envaseRepository, times(1)).findMaxCorrelativoByTipoEnvase("ENV-01");
        verify(envaseRepository, times(1)).save(any(EnvaseFisico.class));
    }

    // ------------------------------------------------------------------------
    // PRUEBA 2: FLUJO DE LLENADO EXITOSO EN BALANZA
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Debería procesar el pesaje en balanza, asignar lote e inyectar auditoría")
    void testProcesarLlenadoEnBalanzaExitoso() throws Exception { // <-- AGREGADO AQUÍ
        String idBolson = "ENV-02-0010";
        double pesoBalanza = 350.5;
        String loteEstructurado = "0626-PP-C";

        EnvaseFisico envaseMock = new EnvaseFisico();
        envaseMock.setIdBolson(idBolson);
        envaseMock.setEstado("DISPONIBLE");

        TrazabilidadLotes fichaEntrada = new TrazabilidadLotes();

        when(envaseRepository.findById(idBolson)).thenReturn(Optional.of(envaseMock));
        when(loteRepository.save(any(TrazabilidadLotes.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(envaseRepository.save(any(EnvaseFisico.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EnvaseFisico envaseLleno = evaluadorService.procesarLlenadoEnBalanza(idBolson, pesoBalanza, loteEstructurado, fichaEntrada);

        assertEquals("NO_DISPONIBLE", envaseLleno.getEstado());
        assertEquals("REC_BALANZA", envaseLleno.getUbicacionActual());
        assertNotNull(envaseLleno.getLoteActual());

        verify(historialRepository, times(1)).save(any(HistorialUsos.class));
    }

    // ------------------------------------------------------------------------
    // PRUEBA 3: SEGURIDAD SGC - BLINDAJE CONTRA ENVASES OBSOLETOS
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Debería arrojar excepción y congelar operación si el bolsón escaneado está OBSOLETO")
    void testBloqueoEnvaseObsoleto() {
        String idBolson = "ENV-01-0005";
        
        EnvaseFisico envaseRoto = new EnvaseFisico();
        envaseRoto.setIdBolson(idBolson);
        envaseRoto.setEstado("OBSOLETO");

        when(envaseRepository.findById(idBolson)).thenReturn(Optional.of(envaseRoto));

        Exception excepcion = assertThrows(ValidacionScrapException.class, () -> {
            evaluadorService.procesarLlenadoEnBalanza(idBolson, 200.0, "0626-OTE-ADST-LY", new TrazabilidadLotes());
        });

        assertEquals("🚨 ENVASE DESCARTADO - NO UTILIZAR", excepcion.getMessage());
        
        verify(loteRepository, never()).save(any(TrazabilidadLotes.class));
        verify(historialRepository, never()).save(any(HistorialUsos.class));
    }

    // ------------------------------------------------------------------------
    // PRUEBA 4: LOGÍSTICA DE SALIDA - DESPACHO INTERPLANTA
    // ------------------------------------------------------------------------
    @Test
    @DisplayName("Debería despachar el bulto alterando su ubicación interplanta sin vaciar su contenido")
    void testDespacharInterplanta() throws Exception { // <-- AGREGADO AQUÍ
        String idBolson = "ENV-02-0089";
        
        EnvaseFisico envaseListo = new EnvaseFisico();
        envaseListo.setIdBolson(idBolson);
        envaseListo.setEstado("NO_DISPONIBLE");
        envaseListo.setUbicacionActual("REC_DEPOSITO");

        when(envaseRepository.findById(idBolson)).thenReturn(Optional.of(envaseListo));
        when(envaseRepository.save(any(EnvaseFisico.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EnvaseFisico envaseEnCamion = evaluadorService.despacharInterplanta(idBolson, "OTE");

        assertEquals("PLANTA_OTE", envaseEnCamion.getUbicacionActual());
        assertEquals("NO_DISPONIBLE", envaseEnCamion.getEstado());
        
        verify(historialRepository, times(1)).save(any(HistorialUsos.class));
    }
}