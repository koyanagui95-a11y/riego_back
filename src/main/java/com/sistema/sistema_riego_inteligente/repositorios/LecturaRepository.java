package com.sistema.sistema_riego_inteligente.Repositorios;

import com.sistema.sistema_riego_inteligente.Entidad.Lectura;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface LecturaRepository extends JpaRepository<Lectura, Long> {

    Page<Lectura> findByUsuarioId(Long usuarioId, Pageable pageable);

    Optional<Lectura> findTopByOrderByIdDesc();

    Optional<Lectura> findTopByUsuarioIdOrderByIdDesc(Long usuarioId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Lectura l WHERE l.usuario.id = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") Long usuarioId);
}