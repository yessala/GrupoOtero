package com.bolsanueva.repository;

import com.bolsanueva.model.HistorialUsos;
import com.bolsanueva.model.EnvaseFisico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * REPOSITORIO JPA PARA EL HISTORIAL Y AUDITORÍA DE USOS
 * @author Autor: Yessalim Salazar
 * @version 6.0 (Motor de Extracción de Datos para Trazabilidad Temporal)
 * * Centraliza las búsquedas cronológicas que alimentarán las pantallas de monitoreo
 * y los reportes descargables de desgaste de bolsones.
 */


@Repository
public interface HistorialUsosRepository extends JpaRepository<HistorialUsos, Long> {

    // BUENAS PRÁCTICAS: Declaración nativa para la línea de tiempo automática del SGC
    List<HistorialUsos> findByEnvaseOrderByFechaMovimientoAsc(EnvaseFisico envase);


//@Repository
//public interface HistorialUsosRepository extends JpaRepository<HistorialUsos, Long> {
//
//    /**
//     * Extrae de forma ordenada toda la actividad histórica que ha tenido un bolsón específico.
//     * Fundamental para calcular la tasa de rotación y vida útil del activo.
//     * * @param idBolson El DNI del contenedor a auditar (Ej: ENV-01-0024).
//     * @return Lista completa de movimientos ordenados desde el más antiguo al más reciente.
//     */
//    // v6.0: Navega a través del objeto 'envase' buscando su 'idBolson' y ordena por 'fechaMovimiento'
//    List<HistorialUsos> findByEnvase_IdBolsonOrderByFechaMovimientoAsc(String idBolson);
}