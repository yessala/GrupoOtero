package com.bolsanueva.controller;

import com.bolsanueva.model.EnvaseFisico;
import com.bolsanueva.model.TrazabilidadLotes;
import com.bolsanueva.service.EvaluadorScrapService;
import com.bolsanueva.exception.ValidacionScrapException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * CONTROLADOR REST API - ECOSISTEMA LOGÍSTICO SGC
 * @author Autor: Yessalim Salazar
 * @version 6.7 (Estándares REST Estrictos y Módulo de Auditoría Integrado)
 */
@RestController
@RequestMapping("/api/bolsones")
@CrossOrigin(origins = "*")
public class BolsonesController {

    @Autowired
    private EvaluadorScrapService evaluadorService;

    // ========================================================================
    // ENDPOINT 1: BOTÓN NUEVO ENVASE (ALTA E IMPRESIÓN)
    // ========================================================================
    @PostMapping("/nuevo")
    public ResponseEntity<?> registrarNuevoEnvase(@RequestParam("tipoEnvase") String tipoEnvase) {
        try {
            EnvaseFisico nuevo = evaluadorService.registrarEImprimirNuevoEnvase(tipoEnvase.toUpperCase());
            return ResponseEntity.status(HttpStatus.CREATED).body(nuevo);
        } catch (ValidacionScrapException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "mensaje", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("status", "ERROR", "mensaje", e.getMessage()));
        }
    }

    // ========================================================================
    // ENDPOINT 2: BALANZA - REGISTRO DE PESO Y ASIGNACIÓN DE LOTE (BUENAS PRÁCTICAS)
    // ========================================================================
    @PostMapping("/llenar")
    public ResponseEntity<?> procesarLlenadoBalanza(
            @RequestParam("idBolson") String idBolson,
            @RequestParam("peso") double pesoEntrada,
            @RequestParam("procedencia") String procedencia,
            @RequestParam("material") String material,
            @RequestParam("laminado") String laminadoStr,
            @RequestParam("tinta") String tintaStr,
            @RequestParam("color") String colorDestino) {
        try {
            java.time.LocalDateTime ahora = java.time.LocalDateTime.now();
            String tipoEnvase = idBolson.substring(0, 6).toUpperCase();

            TrazabilidadLotes fichaMaterial = new TrazabilidadLotes();
            fichaMaterial.setProcedencia(procedencia.toUpperCase());
            fichaMaterial.setMaterial(material.toUpperCase());
            fichaMaterial.setEsLaminado(laminadoStr.equalsIgnoreCase("LY"));
            fichaMaterial.setTieneTinta(tintaStr.equalsIgnoreCase("TY"));
            fichaMaterial.setColorDestino(colorDestino.toUpperCase());
            fichaMaterial.setFechaPesaje(ahora);

            EnvaseFisico envaseProcesado = evaluadorService.procesarLlenadoConCorrelativo(
                    idBolson.toUpperCase(),
                    pesoEntrada,
                    tipoEnvase,
                    fichaMaterial
            );

            // Retornamos el objeto persistido real con estado HTTP 200 OK
            return ResponseEntity.ok(envaseProcesado);

        } catch (ValidacionScrapException e) {
            // Buenas prácticas: Si el negocio falla, devolvemos un 400 Bad Request explícito
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "mensaje", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("status", "ERROR", "mensaje", e.getMessage()));
        }
    }

    // ========================================================================
    // ENDPOINT 3: ÁREA DE VACIADO - LIBERACIÓN DE ENVASES EN TOLVAS
    // ========================================================================
    @PostMapping("/vaciar")
    public ResponseEntity<?> procesarVaciadoTolva(
            @RequestParam("idBolson") String idBolson,
            @RequestParam("estacion") String estacionTrabajo) {
        try {
            EnvaseFisico liberado = evaluadorService.procesarVaciadoEnTolva(
                    idBolson.toUpperCase(),
                    estacionTrabajo.toUpperCase()
            );
            return ResponseEntity.ok(Map.of(
                    "status", "OK",
                    "mensaje", "EL ENVASE " + liberado.getIdBolson() + " PASÓ A ESTADO: " + liberado.getEstado()
            ));
        } catch (ValidacionScrapException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "mensaje", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("status", "ERROR", "mensaje", e.getMessage()));
        }
    }

    // ========================================================================
    // ENDPOINT 4: LOGÍSTICA DE SALIDA - DESPACHO INTERPLANTA
    // ========================================================================
    @PostMapping("/despacho")
    public ResponseEntity<?> despacharBulto(
            @RequestParam("idBolson") String idBolson,
            @RequestParam("destino") String plantaDestino) {
        try {
            EnvaseFisico despachado = evaluadorService.despacharInterplanta(
                    idBolson.toUpperCase(),
                    plantaDestino.toUpperCase()
            );
            return ResponseEntity.ok(Map.of(
                    "status", "OK",
                    "mensaje", "DESPACHADO CON ÉXITO A: " + despachado.getUbicacionActual()
            ));
        } catch (ValidacionScrapException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "mensaje", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("status", "ERROR", "mensaje", e.getMessage()));
        }
    }

    // ========================================================================
    // NUEVO ENDPOINT 5: AUDITORÍA - CONSULTA DE ESTADO HISTÓRICO
    // ========================================================================
    @GetMapping("/consultar/{idBolson}")
    public ResponseEntity<?> consultarEstadoTrazabilidad(@PathVariable String idBolson) {
        try {
            // Delegamos la búsqueda al service para mantener las capas separadas
            EnvaseFisico envase = evaluadorService.buscarPorId(idBolson.toUpperCase());
            return ResponseEntity.ok(envase);
        } catch (ValidacionScrapException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "ERROR", "mensaje", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "ERROR", "mensaje", e.getMessage()));
        }
    }

    // ========================================================================
    // NUEVO ENDPOINT 6: REIMPRESIÓN - REGENERACIÓN DE ETIQUETA DAÑADA
    // ========================================================================
    @GetMapping("/reimprimir/{idBolson}")
    public ResponseEntity<?> reimprimirEtiquetaDeteriorada(@PathVariable String idBolson) {
        try {
            evaluadorService.dispararReimpresionEtiqueta(idBolson.toUpperCase());
            return ResponseEntity.ok(Map.of(
                    "status", "OK",
                    "mensaje", "Comando de impresión térmica enviado con éxito al servidor de planta."
            ));
        } catch (ValidacionScrapException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "mensaje", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("status", "ERROR", "mensaje", e.getMessage()));
        }
    }

    // ========================================================================
    // NUEVO ENDPOINT 7: HISTORIAL COMPLETO Y FILTRADO POR FECHAS (BUENAS PRÁCTICAS)
    // ========================================================================
    @GetMapping("/historial/{idBolson}")
    public ResponseEntity<?> consultarHistorialCompleto(
            @PathVariable String idBolson,
            @RequestParam(value = "desde", required = false) String desdeStr,
            @RequestParam(value = "hasta", required = false) String hastaStr) {
        try {
            // Solicitamos la lista cronológica de usos al service core
            java.util.List<com.bolsanueva.model.HistorialUsos> historial =
                    evaluadorService.obtenerHistorialMovimientos(idBolson.toUpperCase(), desdeStr, hastaStr);

            return ResponseEntity.ok(historial);
        } catch (ValidacionScrapException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "mensaje", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("status", "ERROR", "mensaje", e.getMessage()));
        }
    }

    // ========================================================================
    // NUEVO ENDPOINT 8: AUDITORÍA - DECOMISO Y BAJA DEFINITIVA
    // ========================================================================
    @PostMapping("/baja")
    public ResponseEntity<?> darDeBajaContenedor(
            @RequestParam("idBolson") String idBolson,
            @RequestParam("motivo") String motivo) {
        try {
            EnvaseFisico obsoleto = evaluadorService.procesarBajaDefinitiva(idBolson.toUpperCase(), motivo);
            return ResponseEntity.ok(Map.of(
                    "status", "OK",
                    "mensaje", "El envase " + obsoleto.getIdBolson() + " ha sido retirado de la circulación y marcado como OBSOLETO."
            ));
        } catch (ValidacionScrapException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "mensaje", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("status", "ERROR", "mensaje", e.getMessage()));
        }
    }

}