package com.kizuna.medium.lookup;

import com.kizuna.medium.domain.Medium;
import com.kizuna.medium.domain.MediumRepository;
import com.kizuna.medium.domain.MediumStatus;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 媒体を参照する他モジュールが、店舗内の媒体の存在・状態と現在名を引く口。 */
@Service
@RequiredArgsConstructor
public class MediumLookup {
  private final MediumRepository media;

  @Transactional(readOnly = true)
  public Optional<MediumReference> find(Long storeId, String id) {
    return media.findByIdAndStoreId(id, storeId).map(MediumLookup::reference);
  }

  /** 店舗内に実在する媒体だけを id で引く。他店や存在しない id は結果に含まれない。 */
  @Transactional(readOnly = true)
  public Map<String, MediumReference> findAll(Long storeId, Collection<String> ids) {
    if (ids.isEmpty()) return Map.of();
    return media.findAllByStoreIdAndIdIn(storeId, ids).stream()
        .map(MediumLookup::reference)
        .collect(Collectors.toUnmodifiableMap(MediumReference::id, Function.identity()));
  }

  private static MediumReference reference(Medium m) {
    return new MediumReference(m.getId(), m.getName(), m.getStatus() == MediumStatus.ACTIVE);
  }
}
