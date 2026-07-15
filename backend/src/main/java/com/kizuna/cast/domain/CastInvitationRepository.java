package com.kizuna.cast.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CastInvitationRepository extends JpaRepository<CastInvitation, String> {

  Optional<CastInvitation> findByToken(String token);

  List<CastInvitation> findByCastIdAndStatus(String castId, CastInvitation.Status status);

  List<CastInvitation> findByCastIdIn(Collection<String> castIds);
}
