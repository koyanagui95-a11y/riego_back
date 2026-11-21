package com.sistema.sistema_riego_inteligente.Repositorios;

import com.sistema.sistema_riego_inteligente.Entidad.ConfiguracionSensor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfiguracionSensorRepository extends JpaRepository<ConfiguracionSensor, Long> {
    List<ConfiguracionSensor> findByUsuarioId(Long usuarioId);
    Optional<ConfiguracionSensor> findByUsuarioIdAndTipoSensor(Long usuarioId, String tipoSensor);

    @Modifying
    @Transactional
    @Query("DELETE FROM ConfiguracionSensor c WHERE c.usuario.id = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") Long usuarioId);
}