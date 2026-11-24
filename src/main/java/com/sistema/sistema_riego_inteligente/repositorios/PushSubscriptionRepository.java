package com.sistema.sistema_riego_inteligente.Repositorios;

import com.sistema.sistema_riego_inteligente.Entidad.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    List<PushSubscription> findByUsuarioId(Long usuarioId);
    void deleteByEndpoint(String endpoint);
    void deleteByUsuarioId(Long usuarioId);
}