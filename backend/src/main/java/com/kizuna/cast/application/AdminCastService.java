package com.kizuna.cast.application;

import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastPersonEnrollmentView;
import com.kizuna.cast.domain.CastPersonSummaryView;
import com.kizuna.cast.domain.CastPersonView;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.exception.NotFoundException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCastService {
  private final CastRepository people;
  private final CastEnrollmentRepository enrollments;

  @Transactional(readOnly = true)
  public Page<CastPersonSummaryView> list(String search, Pageable pageable) {
    String term = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
    String pattern = "%" + EscapeCharacter.DEFAULT.escape(term) + "%";
    return people.searchPeople(pattern, pageable);
  }

  @Transactional(readOnly = true)
  public CastPersonView get(Long id) {
    return people.findPerson(id).orElseThrow(() -> new NotFoundException("キャスト本人が見つかりません"));
  }

  @Transactional(readOnly = true)
  public Page<CastPersonEnrollmentView> enrollments(Long id, Pageable pageable) {
    if (!people.existsById(id)) throw new NotFoundException("キャスト本人が見つかりません");
    return enrollments.findPersonEnrollments(id, pageable);
  }
}
