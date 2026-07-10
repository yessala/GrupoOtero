package com.bolsanueva;

import com.bolsanueva.model.EnvaseFisico;
import com.bolsanueva.model.TrazabilidadLotes;
import com.bolsanueva.model.HistorialUsos;
import com.bolsanueva.repository.EnvaseFisicoRepository;
import com.bolsanueva.repository.TrazabilidadLotesRepository;
import com.bolsanueva.repository.HistorialUsosRepository;
import com.bolsanueva.service.EvaluadorScrapService;
import com.bolsanueva.service.BarcodeService;
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
 * @author Autor: Yessalim Salazar
 * @version 6.9 (Refactorización de Firmas, Cobertura Anti-Duplicados y Decomisos)
 */
@ExtendWith(MockitoExtension.class)
class AppTest {

    @Mock
    private EnvaseFisicoRepository envaseRepository;

    @Mock
    private TrazabilidadLotesRepository loteRepository;

    @Mock
    private HistorialUsosRepository historialRepository;

    // CORRECCIÓN: Se añade el Mock de la ticketera para evitar NullPointerException en el flujo
    @Mock
    private BarcodeService barcodeService;

    @InjectMocks
    private EvaluadorScrapService evaluadorService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ========================================================================
    // PRUEBA 1: BOTÓN NUEVO ENVASE E IMPRESIÓN ASOCIADA
    // ========================================================================
    @Test
    @DisplayName("Debería registrar e imprimir un nuevo envase autocalculando su correlativo fidedigno")
    void testRegistrarEImprimirNuevoEnvase() throws Exception {
        when(envaseRepository.findMaxCorrelativoByTipoEnvase("ENV-01")).thenReturn(Optional.of(42));
        when(envaseRepository.save(any(EnvaseFisico.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EnvaseFisico resultado = evaluadorService.registrarEImprimirNuevoEnvase("ENV-01");

        assertNotNull(resultado);
        assertEquals("ENV-01-0043", resultado.getIdBolson());
        assertEquals("DISPONIBLE", resultado.getEstado());
        assertEquals("REC_DEPOSITO", resultado.getUbicacionActual());

        verify(envaseRepository, times(1)).findMaxCorrelativoByTipoEnvase("ENV-01");
        verify(envaseRepository, times(1)).save(any(EnvaseFisico.class));
        verify(barcodeService, times(1)).generarCodigoBarraCompleto(anyString(), anyString());
    }

    // ========================================================================
    // PRUEBA 2: FLUJO DE LLENADO EXITOSO EN BALANZA (CAMINO IDEAL - 3 PARÁMETROS)
    // ========================================================================
    @Test
    @DisplayName("Debería procesar el pesaje en balanza, asignar lote e inyectar auditoría sin parámetros muertos")
    void testProcesarLlenadoEnBalanzaExitoso() throws Exception {
        String idBolson = "ENV-02-0010";
        double pesoBalanza = 350.5;

        EnvaseFisico envaseMock = new EnvaseFisico();
        envaseMock.setIdBolson(idBolson);
        envaseMock.setEstado("DISPONIBLE");

        // Configuración de la ficha normativa para evitar fallos por subcadenas vacías
        TrazabilidadLotes fichaEntrada = new TrazabilidadLotes();
        fichaEntrada.setColorDestino("BLANCO");
        fichaEntrada.setMaterial("PP");
        fichaEntrada.setProcedencia("AGR");

        when(envaseRepository.findById(idBolson)).thenReturn(Optional.of(envaseMock));
        when(loteRepository.countByTipoEnvase("ENV-02")).thenReturn(0L);
        when(loteRepository.save(any(TrazabilidadLotes.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(envaseRepository.save(any(EnvaseFisico.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // CORRECCIÓN: Se remueve el argumento fantasma 'loteEstructurado', adaptándose a la firma limpia
        EnvaseFisico envaseLleno = evaluadorService.procesarLlenadoEnBalanza(idBolson, pesoBalanza, fichaEntrada);

        assertNotNull(envaseLleno);
        assertEquals("NO_DISPONIBLE", envaseLleno.getEstado());
        // CORRECCIÓN SGC: El destino inmutable según especificación de planta es tránsito
        assertEquals("DEPOSITO_TRANSITO", envaseLleno.getUbicacionActual());
        assertNotNull(envaseLleno.getLoteActual());

        verify(historialRepository, times(1)).save(any(HistorialUsos.class));
        verify(barcodeService, times(1)).generarCodigoBarraCompleto(anyString(), anyString());
    }

    // ========================================================================
    // PRUEBA 3: SEGURIDAD SGC - BLINDAJE CONTRA ENVASES OBSOLETOS
    // ========================================================================
    @Test
    @DisplayName("Debería arrojar excepción y congelar operación si el bolsón escaneado está OBSOLETO")
    void testBloqueoEnvaseObsoleto() {
        String idBolson = "ENV-01-0005";

        EnvaseFisico envaseRoto = new EnvaseFisico();
        envaseRoto.setIdBolson(idBolson);
        envaseRoto.setEstado("OBSOLETO");

        when(envaseRepository.findById(idBolson)).thenReturn(Optional.of(envaseRoto));

        // CORRECCIÓN: Ajuste a 3 parámetros
        Exception excepcion = assertThrows(ValidacionScrapException.class, () -> {
            evaluadorService.procesarLlenadoEnBalanza(idBolson, 200.0, new TrazabilidadLotes());
        });

        assertEquals("🚨 ENVASE DESCARTADO - NO UTILIZAR", excepcion.getMessage());
        verify(loteRepository, never()).save(any(TrazabilidadLotes.class));
    }

    // ========================================================================
    // PRUEBA 4: SEGURIDAD SGC -🔒 BLINDAJE ANTI-DOBLE LLENADO (NUEVO)
    // ========================================================================
    @Test
    @DisplayName("Debería rebotar la transacción si el bolsón ya se encuentra lleno o en tránsito")
    void testBloqueoLlenadoConsecutivo() {
        String idBolson = "ENV-02-0022";

        EnvaseFisico envaseOcupado = new EnvaseFisico();
        envaseOcupado.setIdBolson(idBolson);
        envaseOcupado.setEstado("NO_DISPONIBLE"); // Simulamos fallo detectado en la matrix

        when(envaseRepository.findById(idBolson)).thenReturn(Optional.of(envaseOcupado));

        Exception excepcion = assertThrows(ValidacionScrapException.class, () -> {
            evaluadorService.procesarLlenadoEnBalanza(idBolson, 400.0, new TrazabilidadLotes());
        });

        assertTrue(excepcion.getMessage().contains("ya cuenta con un pesaje activo"));
        verify(loteRepository, never()).save(any(TrazabilidadLotes.class));
        verify(historialRepository, never()).save(any(HistorialUsos.class));
    }

    // ========================================================================
    // PRUEBA 5: REGLA DE ORO SGC - 🔒 CONTROL DE DECOMISO EN VACÍO (NUEVO)
    // ========================================================================
    @Test
    @DisplayName("Debería rechazar el decomiso si el supervisor intenta dar de baja un bolsón que contiene carga")
    void testRechazoBajaConLoteActivo() {
        String idBolson = "ENV-01-0999";

        EnvaseFisico envaseConCarga = new EnvaseFisico();
        envaseConCarga.setIdBolson(idBolson);
        envaseConCarga.setEstado("NO_DISPONIBLE"); // Está lleno

        when(envaseRepository.findById(idBolson)).thenReturn(Optional.of(envaseConCarga));

        Exception excepcion = assertThrows(ValidacionScrapException.class, () -> {
            evaluadorService.procesarBajaDefinitiva(idBolson, "Rotura por autoelevador");
        });

        assertTrue(excepcion.getMessage().contains("no se puede decomisar porque contiene un lote activo"));
        verify(envaseRepository, never()).save(any(EnvaseFisico.class));
    }

    // ========================================================================
    // PRUEBA 6: LOGÍSTICA DE SALIDA - DESPACHO INTERPLANTA
    // ========================================================================
    @Test
    @DisplayName("Debería despachar el bulto alterando su ubicación interplanta si posee carga activa")
    void testDespacharInterplanta() throws Exception {
        String idBolson = "ENV-02-0089";

        EnvaseFisico envaseListo = new EnvaseFisico();
        envaseListo.setIdBolson(idBolson);
        envaseListo.setEstado("NO_DISPONIBLE"); // Cumple la regla logística: no está vacío
        envaseListo.setUbicacionActual("REC_DEPOSITO");

        when(envaseRepository.findById(idBolson)).thenReturn(Optional.of(envaseListo));
        when(envaseRepository.save(any(EnvaseFisico.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EnvaseFisico envaseEnCamion = evaluadorService.despacharInterplanta(idBolson, "OTE");

        assertEquals("PLANTA_OTE", envaseEnCamion.getUbicacionActual());
        assertEquals("NO_DISPONIBLE", envaseEnCamion.getEstado());

        verify(historialRepository, times(1)).save(any(HistorialUsos.class));
    }
}