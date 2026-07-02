package com.bolsanueva.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * ENTIDAD DE AUDITORÍA: HISTORIAL CRONOLÓGICO DE USOS
 * @author Autor: Yessalim Salazar
 * @version 6.0 (Estructura de Relaciones Relacionales del SGC)
 */
@Entity
@Table(name = "historial_usos")
@Data // Lombok genera automáticamente todos los getters y setters requeridos
public class HistorialUsos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idLog;

    // Relación directa con el activo físico en el maestro
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_bolson", nullable = false)
    private EnvaseFisico envase;

    // Relación directa con la ficha de material (puede ser null si el bolsón se mueve vacío)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_lote", nullable = true)
    private TrazabilidadLotes loteAsociado;

    @Column(length = 50, nullable = false)
    private String operacion; // "LLENADO", "VACIADO", "DESPACHO_INTERPLANTA"

    @Column(length = 30, nullable = false)
    private String ubicacionOrigen;

    @Column(length = 30, nullable = false)
    private String ubicacionDestino;

    @Column(nullable = false)
    private LocalDateTime fechaMovimiento;

    @Column(length = 255)
    private String detalleAuditoria;
}