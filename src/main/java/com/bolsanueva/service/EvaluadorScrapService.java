package com.bolsanueva.service;

import com.bolsanueva.model.EnvaseFisico;
import com.bolsanueva.model.TrazabilidadLotes;
import com.bolsanueva.model.HistorialUsos;
import com.bolsanueva.repository.EnvaseFisicoRepository;
import com.bolsanueva.repository.TrazabilidadLotesRepository;
import com.bolsanueva.repository.HistorialUsosRepository;
import com.bolsanueva.exception.ValidacionScrapException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * SERVICIO CORE DEL SGC (SISTEMA DE GESTIÓN DE CALIDAD) - PLANTA RECUPERADORA
 * @author Autor: Yessalim Salazar
 * @version 6.3 (Nomenclatura Homologada a 5 Dígitos y Timestamp ISO)
 */
@Service
public class EvaluadorScrapService {

    @Autowired
    private EnvaseFisicoRepository envaseRepository;

    @Autowired
    private TrazabilidadLotesRepository loteRepository;

    @Autowired
    private HistorialUsosRepository historialRepository;

    @Autowired
    private BarcodeService barcodeService;

    @Transactional
    public EnvaseFisico registrarEImprimirNuevoEnvase(String tipoEnvase) throws Exception {
        Optional<Integer> maxCorrelativo = envaseRepository.findMaxCorrelativoByTipoEnvase(tipoEnvase);
        int siguienteNumero = maxCorrelativo.orElse(0) + 1;

        // El DNI invariable del bulto se mantiene con el formato de alta base
        String idBolson = tipoEnvase + "-" + String.format("%04d", siguienteNumero);

        EnvaseFisico nuevoEnvase = new EnvaseFisico();
        nuevoEnvase.setIdBolson(idBolson);
        nuevoEnvase.setTipoEnvase(tipoEnvase);
        nuevoEnvase.setCorrelativo(siguienteNumero);
        nuevoEnvase.setEstado("DISPONIBLE");
        nuevoEnvase.setUbicacionActual("REC_DEPOSITO");
        nuevoEnvase.setLoteActual(null);

        nuevoEnvase = envaseRepository.save(nuevoEnvase);
        barcodeService.generarCodigoBarraCompleto(nuevoEnvase.getIdBolson(), nuevoEnvase.getIdBolson());

        return nuevoEnvase;
    }

    @Transactional
    public EnvaseFisico procesarLlenadoConCorrelativo(String idBolson, double pesoNeto, String tipoEnvase, TrazabilidadLotes loteObjeto) throws Exception {
        
        if (pesoNeto <= 0) {
            throw new ValidacionScrapException("ERROR SGC: El peso registrado debe ser mayor a 0 kg. Operación abortada.");
        }

        EnvaseFisico envase = envaseRepository.findById(idBolson)
                .orElseThrow(() -> new ValidacionScrapException("ERROR SGC: El envase '" + idBolson + "' no existe."));

        if ("OBSOLETO".equalsIgnoreCase(envase.getEstado())) {
            throw new ValidacionScrapException("🚨 ENVASE DESCARTADO - NO UTILIZAR");
        }

        // 1. Contador perpetuo por tipo de envase
        long totalHistorico = loteRepository.countByTipoEnvase(tipoEnvase);
        long proximoCorrelativo = totalHistorico + 1;
        
        // CORRECCIÓN: Ajustado a 5 dígitos fijos según especificación de planta (Ej: "00001")
        String xxxxx = String.format("%05d", proximoCorrelativo);
        
        // 2. Fragmentación de fecha del servidor (MMYY)
        String mmyy = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMyy"));
        String inicialColor = loteObjeto.getColorDestino().substring(0, 1).toUpperCase();
        String loteEstructurado = "";

        // 3. Matriz Oficial de Loteo SGC de la Planta
        switch (tipoEnvase) {
            case "ENV-01":
                // MMYY-XXXXX-PROCEDENCIA-MATERIAL-LAMINADO-TINTA-COLOR
                loteEstructurado = String.format("%s-%s-%s-%s-%s-%s-%s",
                        mmyy, xxxxx, 
                        loteObjeto.getProcedencia().toUpperCase(), 
                        loteObjeto.getMaterial().toUpperCase(),
                        loteObjeto.isEsLaminado() ? "LY" : "LN",
                        loteObjeto.isTieneTinta() ? "TY" : "TN",
                        inicialColor);
                break;
                
            case "ENV-02":
                // MMYY-XXXXX-PP-COLOR
                loteEstructurado = String.format("%s-%s-PP-%s", mmyy, xxxxx, inicialColor);
                break;
                
            case "ENV-03":
                // MMYY-XXXXX-TRIP-COLOR
                loteEstructurado = String.format("%s-%s-TRIP-%s", mmyy, xxxxx, inicialColor);
                break;
                
            case "ENV-04":
                // MMYY-XXXXX-TRIL-COLOR
                loteEstructurado = String.format("%s-%s-TRIL-%s", mmyy, xxxxx, inicialColor);
                break;
                
            case "ENV-05":
                // MMYY-XXXXX-PPL-COLOR (Basado en ejemplo: 0626-00001-PPL-B)
                loteEstructurado = String.format("%s-%s-PPL-%s", mmyy, xxxxx, inicialColor);
                break;
                
            default:
                throw new ValidacionScrapException("🚨 Tipo de contenedor '" + tipoEnvase + "' no paramétrico en SGC.");
        }

        // 4. Inyección y Persistencia de la Ficha de Contenido Normativa
        loteObjeto.setIdLote(loteEstructurado);
        loteObjeto.setCorrelativoNumerico(proximoCorrelativo);
        loteObjeto.setPesoNeto(pesoNeto);
        loteObjeto.setFechaPesaje(LocalDateTime.now()); // Registra DD/MM/YYYY HH:MM:SS automáticamente
        TrazabilidadLotes lotePersistido = loteRepository.save(loteObjeto);

        // 5. Actualización del maestro de contenedores
        envase.setEstado("NO_DISPONIBLE");
        envase.setUbicacionActual("DEPOSITO_TRANSITO");
        envase.setLoteActual(lotePersistido);
        envase = envaseRepository.save(envase);

        // 6. Registro del track de auditoría industrial
        HistorialUsos log = new HistorialUsos();
        log.setEnvase(envase);
        log.setLoteAsociado(lotePersistido);
        log.setOperacion("LLENADO");
        log.setUbicacionOrigen("REC_DEPOSITO");
        log.setUbicacionDestino("DEPOSITO_TRANSITO");
        log.setFechaMovimiento(LocalDateTime.now());
        log.setDetalleAuditoria("Pesaje en báscula completado con éxito bajo norma ISO.");
        historialRepository.save(log);

        // 7. Salida a disco de etiqueta física PNG
        barcodeService.generarCodigoBarraCompleto(loteEstructurado, loteEstructurado);

        return envase;
    }

    // MÉTODO PUENTE PARA COMPATIBILIDAD CON APPTEST
    @Transactional
    public EnvaseFisico procesarLlenadoEnBalanza(String idBolson, double pesoNeto, String loteIgnorado, TrazabilidadLotes loteObjeto) throws Exception {
        String tipoEnvase = idBolson.substring(0, 6).toUpperCase();
        return this.procesarLlenadoConCorrelativo(idBolson, pesoNeto, tipoEnvase, loteObjeto);
    }

    @Transactional
    public EnvaseFisico procesarVaciadoEnTolva(String idBolson, String estacionTrabajo) throws Exception {
        EnvaseFisico envase = envaseRepository.findById(idBolson)
                .orElseThrow(() -> new ValidacionScrapException("ERROR SGC: Envase no identificado en tolva."));

        if ("OBSOLETO".equalsIgnoreCase(envase.getEstado())) {
            throw new ValidacionScrapException("🚨 ENVASE DESCARTADO - NO UTILIZAR");
        }

        TrazabilidadLotes loteQueTenia = envase.getLoteActual();

        envase.setEstado("DISPONIBLE");
        envase.setLoteActual(null);
        envase.setUbicacionActual("REC_DEPOSITO");
        envase = envaseRepository.save(envase);

        HistorialUsos log = new HistorialUsos();
        log.setEnvase(envase);
        log.setLoteAsociado(loteQueTenia);
        log.setOperacion("VACIADO");
        log.setUbicacionOrigen(estacionTrabajo);
        log.setUbicacionDestino("REC_DEPOSITO");
        log.setFechaMovimiento(LocalDateTime.now());
        log.setDetalleAuditoria("Vaciado y liberación de envase en la estación: " + estacionTrabajo);
        historialRepository.save(log);

        return envase;
    }

    @Transactional
    public EnvaseFisico despacharInterplanta(String idBolson, String plantaDestino) throws Exception {
        EnvaseFisico envase = envaseRepository.findById(idBolson)
                .orElseThrow(() -> new ValidacionScrapException("ERROR LOGÍSTICA: Envase no localizado para despacho."));

        if ("OBSOLETO".equalsIgnoreCase(envase.getEstado())) {
            throw new ValidacionScrapException("🚨 ENVASE DESCARTADO - NO UTILIZAR");
        }

        TrazabilidadLotes loteContenido = envase.getLoteActual();
        String ubicacionDestinoMapeada = "PLANTA_" + plantaDestino.toUpperCase(); 

        envase.setUbicacionActual(ubicacionDestinoMapeada);
        envase = envaseRepository.save(envase);

        HistorialUsos log = new HistorialUsos();
        log.setEnvase(envase);
        log.setLoteAsociado(loteContenido);
        log.setOperacion("DESPACHO_INTERPLANTA");
        log.setUbicacionOrigen("REC_DEPOSITO");
        log.setUbicacionDestino(ubicacionDestinoMapeada);
        log.setFechaMovimiento(LocalDateTime.now());
        log.setDetalleAuditoria("Bolsón despachado en camión logístico interplanta.");
        historialRepository.save(log);

        return envase;
    }
}