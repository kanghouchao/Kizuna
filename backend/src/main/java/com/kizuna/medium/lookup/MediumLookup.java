package com.kizuna.medium.lookup;

import com.kizuna.medium.domain.MediumRepository;
import com.kizuna.medium.domain.MediumStatus;
import java.util.Optional;
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
    return media
        .findByIdAndStoreId(id, storeId)
        .map(
            m -> new MediumReference(m.getId(), m.getName(), m.getStatus() == MediumStatus.ACTIVE));
  }
}
