package com.sistema.sistema_riego_inteligente.Repositorios;

import com.sistema.sistema_riego_inteligente.Entidad.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findByUsuarioId(Long usuarioId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    @Modifying
    @Transactional
    void deleteByEndpoint(String endpoint);

    @Modifying
    @Transactional
    void deleteByUsuarioId(Long usuarioId);
}
