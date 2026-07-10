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
    public EnvaseFisico procesarLlenadoEnBalanza(String idBolson, double pesoNeto, TrazabilidadLotes loteObjeto) throws Exception {
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

        // Ahora este metodo va a compilar de una porque ya existe en el HistorialUsosRepository
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

    /**
     * Procesa el decomiso definitivo de un bulto defectuoso.
     * BUENAS PRÁCTICAS: Restricción estricta de estado vacío (DISPONIBLE) para proteger material activo.
     */
    @Transactional
    public EnvaseFisico procesarBajaDefinitiva(String idBolson, String motivoDescarte) throws Exception {
        EnvaseFisico envase = envaseRepository.findById(idBolson)
                .orElseThrow(() -> new ValidacionScrapException("El bulto '" + idBolson + "' no existe."));

        if ("OBSOLETO".equalsIgnoreCase(envase.getEstado())) {
            throw new ValidacionScrapException("El bolsón ya se encuentra dado de baja en el SGC.");
        }

        // ========================================================================
        // 🔒 REGLA DE ORO DE PLANTA: CONTROL DE CONTENEDOR VACÍO
        // ========================================================================
        if (!"DISPONIBLE".equalsIgnoreCase(envase.getEstado())) {
            throw new ValidacionScrapException("🚨 RECHAZO SGC: El envase '" + idBolson +
                    "' no se puede decomisar porque contiene un lote activo en tránsito. " +
                    "Debe registrar el vaciado en Tolva antes de proceder con la baja definitiva.");
        }

        String ubicacionAnterior = envase.getUbicacionActual();

        // Modificamos el maestro: pasa a OBSOLETO y va a zona de descarte (loteActual ya es null por estar DISPONIBLE)
        envase.setEstado("OBSOLETO");
        envase.setUbicacionActual("ZONA_DESCARTE");
        envase = envaseRepository.save(envase);

        // Asentamos el hito en la línea de tiempo para la auditoría
        HistorialUsos log = new HistorialUsos();
        log.setEnvase(envase);
        log.setLoteAsociado(null); // Entra nulo de forma segura ya que el bulto estaba vacío
        log.setOperacion("BAJA_SGC");
        log.setUbicacionOrigen(ubicacionAnterior);
        log.setUbicacionDestino("ZONA_DESCARTE");
        log.setFechaMovimiento(LocalDateTime.now());
        log.setDetalleAuditoria("DECOMISO INDUSTRIAL - Motivo: " + motivoDescarte.toUpperCase());
        historialRepository.save(log);

        return envase;
    }

    // ========================================================================
    // MÓDULO DE TRANSFORMACIÓN LOGÍSTICA: VACIADO Y CO-PROCESAMIENTO DINÁMICO
    // ========================================================================

    @Transactional(rollbackFor = Exception.class)
    public EnvaseFisico procesarTransformacionIndustrial(com.bolsanueva.dto.TransformacionScrapDTO dto) throws Exception {

        if (dto.getPesoDestino() <= 0) {
            throw new ValidacionScrapException("ERROR SGC: El peso resultante de la estación debe ser mayor a 0 kg.");
        }

        // Variables de herencia de trazabilidad inmutable
        String materialHeredado = dto.getMaterialManual();
        String procedenciaHeredada = dto.getProcedenciaManual();
        boolean esLaminadoHeredado = dto.isLaminadoManual();
        boolean tieneTintaHeredado = dto.isTintaManual();
        String colorHeredado = dto.getColorManual();

        // -----------------------------------------------------------------
        // PASO 1: DEPURACIÓN Y VACIADO DEL ENVASE DE ORIGEN (SI EXISTE)
        // -----------------------------------------------------------------
        if (dto.getIdBolsonOrigen() != null && !dto.getIdBolsonOrigen().isEmpty() && !"TORTA".equalsIgnoreCase(dto.getIdBolsonOrigen())) {

            EnvaseFisico envaseOrigen = envaseRepository.findById(dto.getIdBolsonOrigen().toUpperCase())
                    .orElseThrow(() -> new ValidacionScrapException("ERROR SGC: El bulto de entrada '" + dto.getIdBolsonOrigen() + "' no existe en el sistema."));

            if ("DISPONIBLE".equalsIgnoreCase(envaseOrigen.getEstado())) {
                throw new ValidacionScrapException("🚨 CONFLICTO SGC: El envase de entrada '" + dto.getIdBolsonOrigen() + "' ya está vacío. Operación abortada.");
            }
            if ("OBSOLETO".equalsIgnoreCase(envaseOrigen.getEstado())) {
                throw new ValidacionScrapException("🚨 ENVASE DESCARTADO: El bulto origen está fuera de servicio por descarte.");
            }

            TrazabilidadLotes loteOrigen = envaseOrigen.getLoteActual();
            if (loteOrigen == null) {
                throw new ValidacionScrapException("ERROR SGC: El bulto origen no cuenta con una ficha de lote activa en MySQL.");
            }

            // Extracción estricta de variables para la herencia hacia el lote hijo
            materialHeredado = loteOrigen.getMaterial();
            procedenciaHeredada = loteOrigen.getProcedencia();
            esLaminadoHeredado = loteOrigen.isEsLaminado();
            tieneTintaHeredado = loteOrigen.isTieneTinta();
            colorHeredado = loteOrigen.getColorDestino();

            // Liberación física del envase consumido para su reingreso al circuito
            envaseOrigen.setEstado("DISPONIBLE");
            envaseOrigen.setLoteActual(null);
            envaseRepository.save(envaseOrigen);

            // Registro en la bitácora industrial del vaciado
            HistorialUsos logVaciado = new HistorialUsos();
            logVaciado.setEnvase(envaseOrigen);
            logVaciado.setLoteAsociado(loteOrigen);
            logVaciado.setOperacion("VACIADO_TRANSFORMACION");
            logVaciado.setUbicacionOrigen(dto.getEstacionTrabajo());
            logVaciado.setUbicacionDestino("REC_DEPOSITO");
            logVaciado.setFechaMovimiento(LocalDateTime.now());
            logVaciado.setDetalleAuditoria("Contenido consumido en " + dto.getEstacionTrabajo() + " para dar origen a lote secundario.");
            historialRepository.save(logVaciado);

        } else if ("TORTA".equalsIgnoreCase(dto.getIdBolsonOrigen())) {
            // Caso Trituradora de Laminación: Reducción virtual de stocks asentada en auditoría
            HistorialUsos logTorta = new HistorialUsos();
            logTorta.setEnvase(null);
            logTorta.setLoteAsociado(null);
            logTorta.setOperacion("CONSUMO_TORTAS");
            logTorta.setUbicacionOrigen("ZONA_EXTRUSION");
            logTorta.setUbicacionDestino(dto.getEstacionTrabajo());
            logTorta.setFechaMovimiento(LocalDateTime.now());
            logTorta.setDetalleAuditoria("Consumo y reducción de stock de tortas de laminación en báscula por: " + dto.getPesoDestino() + " KG.");
            historialRepository.save(logTorta);
        }

        // -----------------------------------------------------------------
        // PASO 2: VERIFICACIÓN O REGISTRO "ON THE FLY" DEL ENVASE DESTINO
        // -----------------------------------------------------------------
        EnvaseFisico envaseDestino;
        String idDestinoRaw = dto.getIdBolsonDestino();

        if (idDestinoRaw == null || idDestinoRaw.isEmpty() || "AUTO".equalsIgnoreCase(idDestinoRaw)) {
            // Generación automatizada del ID correlativo si el operario no escaneó un bulto rígido
            Optional<Integer> maxCorrelativo = envaseRepository.findMaxCorrelativoByTipoEnvase(dto.getTipoEnvaseDestino());
            int siguienteNumero = maxCorrelativo.orElse(0) + 1;
            String nuevoId = dto.getTipoEnvaseDestino() + "-" + String.format("%04d", siguienteNumero);

            envaseDestino = new EnvaseFisico();
            envaseDestino.setIdBolson(nuevoId);
            envaseDestino.setTipoEnvase(dto.getTipoEnvaseDestino());
            envaseDestino.setCorrelativo(siguienteNumero);
            envaseDestino.setEstado("DISPONIBLE");
            envaseDestino.setUbicacionActual("REC_DEPOSITO");
            envaseDestino = envaseRepository.save(envaseDestino);

            // Imprime su cédula inmutable de identidad
            barcodeService.generarCodigoBarraCompleto(envaseDestino.getIdBolson(), envaseDestino.getIdBolson());
        } else {
            // El operario escaneó un envase físico: Verificamos si existe o lo damos de alta automática
            Optional<EnvaseFisico> envaseOpt = envaseRepository.findById(idDestinoRaw.toUpperCase());
            if (envaseOpt.isPresent()) {
                envaseDestino = envaseOpt.get();
                // BLINDAJE INDUSTRIAL CRÍTICO: Control de inyección consecutiva incorporado
                if (!"DISPONIBLE".equalsIgnoreCase(envaseDestino.getEstado())) {
                    throw new ValidacionScrapException("🚨 RECHAZO BÁSCULA: El envase destino '" + idDestinoRaw +
                            "' ya cuenta con un pesaje activo y está en estado [" + envaseDestino.getEstado() + "].");
                }
            } else {
                // Registro "On the Fly" automático si es un bulto virgen ingresando a planta
                int correlativoInt = Integer.parseInt(idDestinoRaw.substring(idDestinoRaw.lastIndexOf("-") + 1));
                envaseDestino = new EnvaseFisico();
                envaseDestino.setIdBolson(idDestinoRaw.toUpperCase());
                envaseDestino.setTipoEnvase(dto.getTipoEnvaseDestino());
                envaseDestino.setCorrelativo(correlativoInt);
                envaseDestino.setEstado("DISPONIBLE");
                envaseDestino.setUbicacionActual("REC_DEPOSITO");
                envaseDestino = envaseRepository.save(envaseDestino);

                barcodeService.generarCodigoBarraCompleto(envaseDestino.getIdBolson(), envaseDestino.getIdBolson());
            }
        }

        // -----------------------------------------------------------------
        // PASO 3: ENSAMBLADO MATRICIAL DEL LOTE RESULTANTE (HIJO)
        // -----------------------------------------------------------------
        long totalHistorico = loteRepository.countByTipoEnvase(dto.getTipoEnvaseDestino());
        long proximoCorrelativo = totalHistorico + 1;
        String xxxxx = String.format("%05d", proximoCorrelativo);
        String mmyy = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMyy"));
        String inicialColor = (colorHeredado != null && !colorHeredado.isEmpty()) ? colorHeredado.substring(0, 1).toUpperCase() : "X";

        String loteEstructurado = switch (dto.getTipoEnvaseDestino()) {
            case "ENV-01" -> String.format("%s-%s-%s-%s-%s-%s-%s",
                    mmyy, xxxxx,
                    procedenciaHeredada.toUpperCase(),
                    materialHeredado.toUpperCase(),
                    esLaminadoHeredado ? "LY" : "LN",
                    tieneTintaHeredado ? "TY" : "TN",
                    inicialColor);

            case "ENV-02" -> String.format("%s-%s-PP-%s", mmyy, xxxxx, inicialColor);
            case "ENV-03" -> String.format("%s-%s-TRIP-%s", mmyy, xxxxx, inicialColor);
            case "ENV-04" -> String.format("%s-%s-TRIL-%s", mmyy, xxxxx, inicialColor);
            case "ENV-05" -> String.format("%s-%s-PPL-%s", mmyy, xxxxx, inicialColor);

            default -> throw new ValidacionScrapException("🚨 Tipo de contenedor destino no parametrizado en matriz SGC.");
        };

        // -----------------------------------------------------------------
        // PASO 4: PERSISTENCIA E INYECCIÓN DE ESTADOS EN CASCADA
        // -----------------------------------------------------------------
        TrazabilidadLotes nuevoLote = new TrazabilidadLotes();
        nuevoLote.setIdLote(loteEstructurado);
        nuevoLote.setTipoEnvase(dto.getTipoEnvaseDestino());
        nuevoLote.setCorrelativoNumerico(proximoCorrelativo);
        nuevoLote.setPesoNeto(dto.getPesoDestino());
        nuevoLote.setMaterial(materialHeredado);
        nuevoLote.setProcedencia(procedenciaHeredada);
        nuevoLote.setEsLaminado(esLaminadoHeredado);
        nuevoLote.setTieneTinta(tieneTintaHeredado);
        nuevoLote.setColorDestino(colorHeredado);
        nuevoLote.setFechaPesaje(LocalDateTime.now());
        TrazabilidadLotes lotePersistido = loteRepository.save(nuevoLote);

        // Actualización del maestro de bultos destino
        envaseDestino.setEstado("NO_DISPONIBLE");
        envaseDestino.setUbicacionActual("DEPOSITO_TRANSITO");
        envaseDestino.setLoteActual(lotePersistido);
        envaseDestino = envaseRepository.saveAndFlush(envaseDestino);

        // Bitácora de llenado y pesaje
        HistorialUsos logLlenado = new HistorialUsos();
        logLlenado.setEnvase(envaseDestino);
        logLlenado.setLoteAsociado(lotePersistido);
        logLlenado.setOperacion("LLENADO_TRANSFORMACION");
        logLlenado.setUbicacionOrigen(dto.getEstacionTrabajo());
        logLlenado.setUbicacionDestino("DEPOSITO_TRANSITO");
        logLlenado.setFechaMovimiento(LocalDateTime.now());
        logLlenado.setDetalleAuditoria("Lote estructurado de salida generado bajo norma ISO. Rendimiento de co-procesamiento completo.");
        historialRepository.save(logLlenado);

        // Emisión de la etiqueta de barras final para el paletizado
        barcodeService.generarCodigoBarraCompleto(loteEstructurado, loteEstructurado);

        return envaseDestino;
    }
}