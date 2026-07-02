package com.bolsanueva.repository;

import com.bolsanueva.model.EnvaseFisico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * REPOSITORIO JPA PARA EL MAESTRO DE ENVASES
 * @author Autor: Yessalim Salazar
 * @version 6.0 (Capa de Acceso a Activos Físicos)
 * * Proporciona los métodos para auditar existencias de bolsones, control de obsoletos
 * y el cálculo secuencial de nuevos envases ingresados al sistema.
 */
@Repository
public interface EnvaseFisicoRepository extends JpaRepository<EnvaseFisico, String> {

    /**
     * Busca el número correlativo más alto registrado en el maestro para un tipo de envase específico.
     * Es la base del algoritmo de numeración continua (Ej: ENV-01-XXXX).
     * * @param tipoEnvase Prefijo del envase a consultar (Ej: "ENV-01", "ENV-02").
     * @return El número entero máximo encontrado dentro de la tabla.
     */
    @Query("SELECT MAX(e.correlativo) FROM EnvaseFisico e WHERE e.tipoEnvase = :tipoEnvase")
    Optional<Integer> findMaxCorrelativoByTipoEnvase(@Param("tipoEnvase") String tipoEnvase);

    
}