package com.kizuna.user.application;

import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 集約を保存し、整合性違反を業務例外へ写像する。
 *
 * <p>@ElementCollection の行（ロール集合・店舗集合・権限集合）はトランザクション commit 時に flush されるため、{@code save} だけでは違反が
 * この捕捉を突き抜けて 500 になる。{@code saveAndFlush} で違反をここで顕在化させ 400 へ変換する。FK 違反は全域ハンドラでは 4xx にならない
 * （一意違反のみが兜底の対象）ため、この写像なしでは救えない。
 *
 * <p>対応表を呼出側から受け取る理由は {@link IntegrityViolations} を参照。写像に無い違反は元の例外がそのまま送出され、全域ハンドラの分類へ落ちる。
 */
final class IntegrityMappedSaves {

  private IntegrityMappedSaves() {}

  static <T> T save(
      JpaRepository<T, ?> repository,
      T entity,
      Map<DbConstraint, Supplier<RuntimeException>> table) {
    try {
      return repository.saveAndFlush(entity);
    } catch (DataIntegrityViolationException ex) {
      throw IntegrityViolations.translate(ex, table);
    }
  }
}
