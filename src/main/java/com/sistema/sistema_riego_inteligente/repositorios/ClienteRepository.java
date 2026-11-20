package com.sistema.sistema_riego_inteligente.Repositorios;

import com.sistema.sistema_riego_inteligente.Entidad.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByUsuarioId(Long usuarioId);
    Optional<Cliente> findByDispositivoId(String dispositivoId);
    List<Cliente> findByEstado(String estado);
    List<Cliente> findAllByOrderByFechaRegistroDesc();
}