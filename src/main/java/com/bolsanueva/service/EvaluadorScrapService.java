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
 * @version 6.8 (Buenas Prácticas, Enhanced Switch y Limpieza de Ámbitos)
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

        // ========================================================================
        // 🔒 AQUÍ VA EL BLINDAJE: CONTROL DE INYECCIÓN CONSECUTIVA EN BALANZA
        // ========================================================================
        if (!"DISPONIBLE".equalsIgnoreCase(envase.getEstado())) {
            String loteActivo = (envase.getLoteActual() != null) ? envase.getLoteActual().getIdLote() : "DESCONOCIDO";
            throw new ValidacionScrapException("🚨 OPERACIÓN RECHAZADA: El bolsón '" + idBolson +
                    "' ya cuenta con un pesaje activo (Lote: " + loteActivo + ") y está en estado [" + envase.getEstado() + "]. " +
                    "Debe ser liberado en la Tolva de Vaciado antes de admitir un nuevo loteo.");
        }

        long totalHistorico = loteRepository.countByTipoEnvase(tipoEnvase);
        long proximoCorrelativo = totalHistorico + 1;

        String xxxxx = String.format("%05d", proximoCorrelativo);
        String mmyy = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMyy"));
        String inicialColor = loteObjeto.getColorDestino().substring(0, 1).toUpperCase();

        String loteEstructurado = switch (tipoEnvase) {
            case "ENV-01" -> String.format("%s-%s-%s-%s-%s-%s-%s",
                    mmyy, xxxxx,
                    loteObjeto.getProcedencia().toUpperCase(),
                    loteObjeto.getMaterial().toUpperCase(),
                    loteObjeto.isEsLaminado() ? "LY" : "LN",
                    loteObjeto.isTieneTinta() ? "TY" : "TN",
                    inicialColor);

            case "ENV-02" -> String.format("%s-%s-PP-%s", mmyy, xxxxx, inicialColor);
            case "ENV-03" -> String.format("%s-%s-TRIP-%s", mmyy, xxxxx, inicialColor);
            case "ENV-04" -> String.format("%s-%s-TRIL-%s", mmyy, xxxxx, inicialColor);
            case "ENV-05" -> String.format("%s-%s-PPL-%s", mmyy, xxxxx, inicialColor);

            default -> throw new ValidacionScrapException("🚨 Tipo de contenedor '" + tipoEnvase + "' no paramétrico en SGC.");
        };

        loteObjeto.setIdLote(loteEstructurado);
        loteObjeto.setCorrelativoNumerico(proximoCorrelativo);
        loteObjeto.setPesoNeto(pesoNeto);
        loteObjeto.setFechaPesaje(LocalDateTime.now());
        TrazabilidadLotes lotePersistido = loteRepository.save(loteObjeto);

        envase.setEstado("NO_DISPONIBLE");
        envase.setUbicacionActual("DEPOSITO_TRANSITO");
        envase.setLoteActual(lotePersistido);
        envase = envaseRepository.save(envase);

        HistorialUsos log = new HistorialUsos();
        log.setEnvase(envase);
        log.setLoteAsociado(lotePersistido);
        log.setOperacion("LLENADO");
        log.setUbicacionOrigen("REC_DEPOSITO");
        log.setUbicacionDestino("DEPOSITO_TRANSITO");
        log.setFechaMovimiento(LocalDateTime.now());
        log.setDetalleAuditoria("Pesaje en báscula completado con éxito bajo norma ISO.");
        historialRepository.save(log);

        barcodeService.generarCodigoBarraCompleto(loteEstructurado, loteEstructurado);

        return envase;
    }

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

    // ========================================================================
    // SECCIÓN DE AUDITORÍA Y TRAZABILIDAD (UNIFICADA Y LIMPIA DE DUPLICADOS)
    // ========================================================================

    /**
     * Localiza los datos históricos del contenedor.
     * Propaga de forma explícita las excepciones controladas del negocio de planta.
     */
    public EnvaseFisico buscarPorId(String idBolson) throws ValidacionScrapException {
        return envaseRepository.findById(idBolson)
                .orElseThrow(() -> new ValidacionScrapException("El bulto '" + idBolson + "' no se encuentra registrado en el SGC."));
    }

    /**
     * Recupera el lote e invoca la ticketera térmica para reimpresión por daño.
     */
    public void dispararReimpresionEtiqueta(String idBolson) throws Exception {
        EnvaseFisico envase = envaseRepository.findById(idBolson)
                .orElseThrow(() -> new ValidacionScrapException("El envase especificado no existe."));

        if (envase.getLoteActual() == null) {
            throw new ValidacionScrapException("El bulto existe pero aún no registra pesaje ni lote asignado en báscula.");
        }

        // Genera el código físico usando las variables inyectadas de la clase superior
        barcodeService.generarCodigoBarraCompleto(envase.getIdBolson(), envase.getLoteActual().getIdLote());
    }

    /**
     * Extrae la línea de tiempo completa del contenedor, aplicando filtros de rango si se solicitan.
     * CORRECCIÓN: Se añade el throws explícito para resolver el error de IntelliJ.
     */
    public java.util.List<HistorialUsos> obtenerHistorialMovimientos(String idBolson, String desdeStr, String hastaStr) throws ValidacionScrapException {

        EnvaseFisico envase = envaseRepository.findById(idBolson)
                .orElseThrow(() -> new ValidacionScrapException("El bulto '" + idBolson + "' no está registrado en el sistema."));

        // Ahora este método va a compilar de una porque ya existe en el HistorialUsosRepository
        java.util.List<HistorialUsos> listaCompleta = historialRepository.findByEnvaseOrderByFechaMovimientoAsc(envase);

        if (desdeStr == null || hastaStr == null || desdeStr.isEmpty() || hastaStr.isEmpty()) {
            return listaCompleta;
        }

        LocalDateTime fechaInicio = java.time.LocalDate.parse(desdeStr).atStartOfDay();
        LocalDateTime fechaFin = java.time.LocalDate.parse(hastaStr).atTime(23, 59, 59);

        return listaCompleta.stream()
                .filter(log -> !log.getFechaMovimiento().isBefore(fechaInicio) && !log.getFechaMovimiento().isAfter(fechaFin))
                .collect(java.util.stream.Collectors.toList());
    }
}