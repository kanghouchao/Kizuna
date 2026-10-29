package com.kizuna.cast.application;

import com.kizuna.cast.api.dto.AdminCastEnrollmentResponse;
import com.kizuna.cast.api.dto.AdminCastResponse;
import com.kizuna.cast.api.dto.AdminCastSummaryResponse;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCastService {
  private final CastRepository people;
  private final CastEnrollmentRepository enrollments;

  @Transactional(readOnly = true)
  public Page<AdminCastSummaryResponse> list(String search, int page, int size) {
    var pageable = pageRequest(page, size);
    String term = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
    String pattern = "%" + EscapeCharacter.DEFAULT.escape(term) + "%";
    return people
        .searchPeople(pattern, pageable)
        .map(
            row ->
                new AdminCastSummaryResponse(row.getId(), row.getDisplayName(), row.getRealName()));
  }

  @Transactional(readOnly = true)
  public AdminCastResponse get(Long id) {
    var row = people.findPerson(id).orElseThrow(() -> new NotFoundException("キャスト本人が見つかりません"));
    return new AdminCastResponse(
        row.getId(), row.getUserId(), row.getDisplayName(), row.getRealName(), row.getBirthDate());
  }

  @Transactional(readOnly = true)
  public Page<AdminCastEnrollmentResponse> enrollments(Long id, int page, int size) {
    var pageable = pageRequest(page, size);
    if (!people.existsById(id)) throw new NotFoundException("キャスト本人が見つかりません");
    return enrollments
        .findPersonEnrollments(id, pageable)
        .map(
            row ->
                new AdminCastEnrollmentResponse(
                    row.getId(),
                    row.getStoreId(),
                    row.getStoreName(),
                    row.getName(),
                    row.getStatus(),
                    row.getStartedOn(),
                    row.getEndedAt()));
  }

  private static PageRequest pageRequest(int page, int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw new ServiceException("page は 0 以上、size は 1〜100 を指定してください");
    }
    return PageRequest.of(page, size);
  }
}
