package com.kizuna.member.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

  Optional<Member> findByPlatformUserId(Long platformUserId);

  Optional<Member> findByMemberCode(String memberCode);

  boolean existsByMemberCode(String memberCode);
}
