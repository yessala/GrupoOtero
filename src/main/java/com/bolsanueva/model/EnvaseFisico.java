package com.bolsanueva.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * ENTIDAD DE CONTROL DE ACTIVOS FÍSICOS (SGC)
 * @author Autor: Yessalim Salazar
 * @version 6.0 (Arquitectura de Inventario Disociado)
 * * Modela el contenedor real (Bolsón) independiente de su contenido. 
 * Gobierna las alertas de exclusión por obsolescencia y geolocalización interplanta.
 */
@Entity
@Table(name = "maestro_envases")
@Data
public class EnvaseFisico {

    @Id
    @Column(name = "id_bolson", length = 15)
    private String idBolson; // Clave Primaria: ENV-XX-XXXX (Ej: ENV-01-0043)

    @Column(name = "tipo_envase", nullable = false, length = 6)
    private String tipoEnvase; // "ENV-01", "ENV-02", "ENV-03", "ENV-04", "ENV-05"

    @Column(name = "correlativo", nullable = false)
    private int correlativo; // Conteo ascendente infinito por tipo de envase

    @Column(name = "estado", nullable = false, length = 20)
    private String estado; // DISPONIBLE, NO_DISPONIBLE, OBSOLETO

    @Column(name = "ubicacion_actual", nullable = false, length = 20)
    private String ubicacionActual; // REC_BALANZA, REC_DEPOSITO, PLANTA_AGR, PLANTA_OTE, PLANTA_UNI

    /**
     * Relación Unidireccional al lote de contenido actual.
     * Si el estado es DISPONIBLE u OBSOLETO, este campo permanece en NULL (Vaciado).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_lote_actual", referencedColumnName = "id_lote", nullable = true)
    private TrazabilidadLotes loteActual; 
}