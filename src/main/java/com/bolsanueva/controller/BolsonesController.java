package com.bolsanueva.controller;

import com.bolsanueva.model.EnvaseFisico;
import com.bolsanueva.model.TrazabilidadLotes;
import com.bolsanueva.model.LoteResponse;
import com.bolsanueva.service.EvaluadorScrapService;
import com.bolsanueva.exception.ValidacionScrapException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * CONTROLADOR REST API - ECOSISTEMA LOGÍSTICO SGC
 * @author Autor: Yessalim Salazar
 * @version 6.0 (Puntos de Acceso para Interfaces de Planta Dedicadas)
 * * Centraliza los disparos de los lectores de códigos de barras en las básculas,
 * tolvas de vaciado y muelles de despacho interplanta.
 */
@RestController
@RequestMapping("/api/bolsones")
@CrossOrigin(origins = "*") // Enlace directo para tu index.html / frontend local
public class BolsonesController {

    @Autowired
    private EvaluadorScrapService evaluadorService;

    // ------------------------------------------------------------------------
    // ENDPOINT 1: BOTÓN NUEVO ENVASE (ALTA E IMPRESIÓN)
    // ------------------------------------------------------------------------
    @PostMapping("/nuevo")
    public ResponseEntity<?> registrarNuevoEnvase(@RequestParam("tipoEnvase") String tipoEnvase) {
        try {
            // Invoca al core para calcular correlativo, persistir y mandar a imprimir
            EnvaseFisico nuevo = evaluadorService.registrarEImprimirNuevoEnvase(tipoEnvase.toUpperCase());
            return ResponseEntity.ok(nuevo);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("ERROR ALTA: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // ENDPOINT 2: BALANZA - REGISTRO DE PESO Y ASIGNACIÓN DE LOTE
    // ------------------------------------------------------------------------
@PostMapping("/llenar")
    public LoteResponse procesarLlenadoBalanza(
            @RequestParam("idBolson") String idBolson,
            @RequestParam("peso") double pesoEntrada,
            @RequestParam("procedencia") String procedencia,
            @RequestParam("material") String material,
            @RequestParam("laminado") String laminadoStr,
            @RequestParam("tinta") String tintaStr,
            @RequestParam("color") String colorDestino) {
        try {
            java.time.LocalDateTime ahora = java.time.LocalDateTime.now();
            String tipoEnvase = idBolson.substring(0, 6).toUpperCase(); // "ENV-01"

            // 1. Instanciar la ficha de auditoría temporal
            TrazabilidadLotes fichaMaterial = new TrazabilidadLotes();
            fichaMaterial.setProcedencia(procedencia.toUpperCase());
            fichaMaterial.setMaterial(material.toUpperCase());
            fichaMaterial.setEsLaminado(laminadoStr.equalsIgnoreCase("LY"));
            fichaMaterial.setTieneTinta(tintaStr.equalsIgnoreCase("TY"));
            fichaMaterial.setColorDestino(colorDestino.toUpperCase());
            fichaMaterial.setFechaPesaje(ahora); // Guardamos DD/MM/YYYY HH:MM completo en BD

            // 2. Delegamos al Service para que busque el correlativo persistente y arme el lote definitivo
            EnvaseFisico envaseProcesado = evaluadorService.procesarLlenadoConCorrelativo(
                    idBolson.toUpperCase(), 
                    pesoEntrada, 
                    tipoEnvase, 
                    fichaMaterial
            );

            return new LoteResponse(
                    envaseProcesado.getIdBolson(),
                    envaseProcesado.getLoteActual().getIdLote(), // El lote generado con su XXXX único
                    envaseProcesado.getUbicacionActual(),
                    "PROCESADO_OK"
            );

        } catch (com.bolsanueva.exception.ValidacionScrapException e) {
            return new LoteResponse(idBolson, null, null, e.getMessage());
        } catch (Exception e) {
            return new LoteResponse(idBolson, null, null, "ERROR CONTROLADOR: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // ENDPOINT 3: ÁREA DE VACIADO - LIBERACIÓN DE ENVASES EN TOLVAS
    // ------------------------------------------------------------------------
    @PostMapping("/vaciar")
    public ResponseEntity<?> procesarVaciadoTolva(
            @RequestParam("idBolson") String idBolson,
            @RequestParam("estacion") String estacionTrabajo) {
        try {
            // Libera el bulto y lo vuelve a poner DISPONIBLE en el depósito
            EnvaseFisico liberado = evaluadorService.procesarVaciadoEnTolva(
                    idBolson.toUpperCase(), 
                    estacionTrabajo.toUpperCase()
            );
            return ResponseEntity.ok("EL ENVASE " + liberado.getIdBolson() + " PASÓ A ESTADO: " + liberado.getEstado());
        } catch (ValidacionScrapException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("ERROR VACIADO: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // ENDPOINT 4: LOGÍSTICA DE SALIDA - DESPACHO INTERPLANTA
    // ------------------------------------------------------------------------
    @PostMapping("/despacho")
    public ResponseEntity<?> despacharBulto(
            @RequestParam("idBolson") String idBolson,
            @RequestParam("destino") String plantaDestino) {
        try {
            // Cambia la ubicación en tiempo real (Ej: PLANTA_OTE o PLANTA_AGR)
            EnvaseFisico despachado = evaluadorService.despacharInterplanta(
                    idBolson.toUpperCase(), 
                    plantaDestino.toUpperCase()
            );
            return ResponseEntity.ok("DESPACHADO CON ÉXITO A: " + despachado.getUbicacionActual());
        } catch (ValidacionScrapException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("ERROR LOGÍSTICA: " + e.getMessage());
        }
    }
}