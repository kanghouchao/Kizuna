package com.kizuna.cast.application;

import com.kizuna.cast.api.dto.PlatformCastEnrollmentResponse;
import com.kizuna.cast.api.dto.PlatformCastResponse;
import com.kizuna.cast.api.dto.PlatformCastSummaryResponse;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScopeExempt;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformCastService {
  private final CastRepository people;
  private final CastEnrollmentRepository enrollments;

  @StoreScopeExempt(reason = "平台級の本人のみを検索し、店舗スコープ表を参照しない")
  @Transactional(readOnly = true)
  public Page<PlatformCastSummaryResponse> list(String search, int page, int size) {
    var pageable = pageRequest(page, size);
    String term = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
    String pattern = "%" + EscapeCharacter.DEFAULT.escape(term) + "%";
    return people
        .searchPeople(pattern, pageable)
        .map(
            row ->
                new PlatformCastSummaryResponse(
                    row.getId(), row.getDisplayName(), row.getRealName()));
  }

  @StoreScopeExempt(reason = "平台級の本人のみを取得し、店舗スコープ表を参照しない")
  @Transactional(readOnly = true)
  public PlatformCastResponse get(Long id) {
    var row = people.findPerson(id).orElseThrow(() -> new NotFoundException("キャスト本人が見つかりません"));
    return new PlatformCastResponse(
        row.getId(),
        row.getPlatformUserId(),
        row.getDisplayName(),
        row.getRealName(),
        row.getBirthDate());
  }

  @StoreScopeExempt(reason = "CAST_PERSON_VIEW を要求する平台専用端点から全店舗の在籍を照会する")
  @Transactional(readOnly = true)
  public Page<PlatformCastEnrollmentResponse> enrollments(Long id, int page, int size) {
    var pageable = pageRequest(page, size);
    if (!people.existsById(id)) throw new NotFoundException("キャスト本人が見つかりません");
    return enrollments
        .findPersonEnrollments(id, pageable)
        .map(
            row ->
                new PlatformCastEnrollmentResponse(
                    row.getId(),
                    row.getStoreId(),
                    row.getStoreName(),
                    row.getName(),
                    row.getStatus(),
                    row.getEndedAt()));
  }

  private static PageRequest pageRequest(int page, int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw new ServiceException("page は 0 以上、size は 1〜100 を指定してください");
    }
    return PageRequest.of(page, size);
  }
}
