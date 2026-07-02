package com.bolsanueva.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * ENTIDAD DE TRAZABILIDAD DE MATERIA PRIMA / WIP / TERMINADO
 * @author Autor: Yessalim Salazar
 * @version 6.3 (Ficha de Contenido Normativa ISO)
 */
@Entity
@Table(name = "trazabilidad_lotes")
@Data
public class TrazabilidadLotes {

    @Id
    @Column(name = "id_lote", length = 50)
    private String idLote; // MMYY-XXXXX-...

    @Column(name = "correlativo_numerico", nullable = false)
    private long correlativoNumerico; // Control numérico continuo interno de planta

    @Column(name = "peso_neto", nullable = false)
    private double pesoNeto; // Inyección directa de la báscula industrial

    @Column(length = 10)
    private String procedencia; // AGR, UNI, OTE

    @Column(length = 10)
    private String material; // RAFI, DOFI, TORT, PP, etc.

    @Column(name = "es_laminado", nullable = false)
    private boolean esLaminado; // true (LY) / false (LN)

    @Column(name = "tiene_tinta", nullable = false)
    private boolean tieneTinta; // true (TY) / false (TN)

    @Column(name = "color_destino", nullable = false, length = 15)
    private String colorDestino; // B (Blanco), C (Color)

    @Column(name = "fecha_pesaje", nullable = false)
    private LocalDateTime fechaPesaje; // Timestamp inmutable: YYYY-MM-DD HH:MM:SS

    public TrazabilidadLotes() {
        // Captura automática de fecha y hora exacta del servidor al instanciarse en balanza
        this.fechaPesaje = LocalDateTime.now();
    }
}