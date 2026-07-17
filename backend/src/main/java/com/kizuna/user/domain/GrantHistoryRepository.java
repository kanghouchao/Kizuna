package com.kizuna.user.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrantHistoryRepository extends JpaRepository<GrantHistory, Long> {

  List<GrantHistory> findByPlatformUserIdOrderByCreatedAtDesc(Long platformUserId);
}
