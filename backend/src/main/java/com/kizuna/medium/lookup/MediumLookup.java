package com.kizuna.medium.lookup;

import com.kizuna.medium.domain.Medium;
import com.kizuna.medium.domain.MediumRepository;
import com.kizuna.medium.domain.MediumStatus;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.ServiceException;
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

  /** 記録に付ける媒体を引き、{@link #selectable} の規則で検査する。 */
  @Transactional(readOnly = true)
  public MediumReference requireSelectable(Long storeId, String id, String current) {
    return selectable(find(storeId, id).orElse(null), current);
  }

  /** 選べるのは店舗内の有効な媒体だけ。記録が既に持つ停止中の媒体（current）は付け替えでないので保てる。 */
  public static MediumReference selectable(MediumReference medium, String current) {
    if (medium == null) throw missing();
    if (!medium.active() && !medium.id().equals(current))
      throw new ServiceException("停止中の媒体は選択できません");
    return medium;
  }

  /** 他店・存在しない媒体、または検査の後に削除された媒体（FK 違反）を指す要求への入力エラー。 */
  public static ServiceException missing() {
    return new ServiceException("媒体が見つかりません。一覧から選択してください");
  }

  /** 記録を flush する。検査の後に媒体が削除された場合も、媒体を指す FK（mediumFk）の違反を同じ入力エラーに揃える。 */
  public static void flush(Runnable flush, DbConstraint mediumFk) {
    IntegrityViolations.translateOnFailure(
        () -> {
          flush.run();
          return null;
        },
        Map.of(mediumFk, MediumLookup::missing));
  }

  /** 記録が持つ媒体 ID を {@link #findAll} の結果から引く。媒体を持たない記録（id が null）は null。 */
  public static MediumReference referenced(Map<String, MediumReference> media, String id) {
    return id == null ? null : referenced(Optional.ofNullable(media.get(id)));
  }

  // 複合 FK が同一店舗の媒体の実在を保証するので、記録が指す媒体を引けないのは不変条件の破れ。
  public static MediumReference referenced(Optional<MediumReference> found) {
    return found.orElseThrow(() -> new IllegalStateException("記録の媒体が見つかりません"));
  }

  private static MediumReference reference(Medium m) {
    return new MediumReference(m.getId(), m.getName(), m.getStatus() == MediumStatus.ACTIVE);
  }
}
