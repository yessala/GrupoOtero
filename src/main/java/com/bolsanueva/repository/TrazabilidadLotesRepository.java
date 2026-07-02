package com.bolsanueva.repository;

import com.bolsanueva.model.TrazabilidadLotes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * REPOSITORIO JPA PARA LA TRAZABILIDAD DE LOTES
 * @author Autor: Yessalim Salazar
 * @version 6.2 (Corrección de Consulta por Mapeo de Atributos)
 */
@Repository
public interface TrazabilidadLotesRepository extends JpaRepository<TrazabilidadLotes, String> {

    /**
     * Cuenta históricamente cuántos lotes se han emitido para un tipo de envase específico.
     * Al usar nativeQuery = true, leemos directamente el formato del lote persistido en la tabla.
     * * @param tipoEnvase Prefijo del contenedor a auditar (Ej: "ENV-01", "ENV-02")
     * @return Cantidad total de registros que coinciden en la base de datos
     */
    @Query(value = "SELECT COUNT(*) FROM trazabilidad_lotes t WHERE t.id_lote LIKE %:tipoEnvase%", nativeQuery = true)
    long countByTipoEnvase(@Param("tipoEnvase") String tipoEnvase);
}