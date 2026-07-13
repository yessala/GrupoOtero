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

    // Declaración nativa para la línea de tiempo automática del SGC
    List<HistorialUsos> findByEnvaseOrderByFechaMovimientoAsc(EnvaseFisico envase);



}