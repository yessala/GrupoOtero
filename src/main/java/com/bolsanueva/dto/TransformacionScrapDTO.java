package com.bolsanueva.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO DE ENLACE DE PROCESOS - PLANTA RECUPERADORA - VERSION OPTIMIZADA CON LOMBOK
 * @author Autor: Yessalim Salazar
 */

    @Setter
    @Getter
    public class TransformacionScrapDTO {
    // Getters y Setters completos
    private String idBolsonOrigen;    // Escaneado (ENV-01 o ENV-03) o "TORTA"
    private String idBolsonDestino;   // Escaneado, vacío o "AUTO" para alta automática
    private String tipoEnvaseDestino; // ENV-02, ENV-03, ENV-05, etc.
    private double pesoDestino;       // Peso registrado en la báscula de salida
    private String estacionTrabajo;   // PELLETIZADORA_1, TRITURADORA_COMUN, etc.

    // Variables opcionales para la estación de Recepción (ENV-01) cuando no hay origen
    private String materialManual;
    private String procedenciaManual;
    private boolean laminadoManual;
    private boolean tintaManual;
    private String colorManual;

}