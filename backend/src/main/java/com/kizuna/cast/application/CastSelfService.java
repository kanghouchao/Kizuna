package com.kizuna.cast.application;

import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastStoreView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本人（キャスト）ポータルの所属店舗解決ユースケース。
 *
 * <p>在籍 id の解決（{@link CastEnrollmentRepository#findIdsByUserId}）と同じく user_id を基点に引き、店舗を限らず
 * 本人の所属店舗をすべて返す。
 */
@Service
@RequiredArgsConstructor
public class CastSelfService {

  private final CastEnrollmentRepository castRepository;

  @Transactional(readOnly = true)
  public List<CastStoreView> myStores(long userId) {
    return castRepository.findStoresByUserId(userId);
  }
}
