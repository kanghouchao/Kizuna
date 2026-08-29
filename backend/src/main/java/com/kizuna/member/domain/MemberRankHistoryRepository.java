package com.kizuna.member.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRankHistoryRepository extends JpaRepository<MemberRankHistory, Long> {

  List<MemberRankHistory> findByMemberIdOrderByIdAsc(Long memberId);
}
