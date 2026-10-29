package com.kizuna.recruitment.infrastructure;

import com.kizuna.cast.domain.ApplicantReferenceCheck;
import com.kizuna.recruitment.domain.ApplicantRepository;
import java.util.Collection;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ApplicantRepositoryReferenceCheck implements ApplicantReferenceCheck {
  private final ApplicantRepository applicants;

  @Override
  public Set<String> findReferencedCastIds(Collection<String> castIds) {
    return applicants.findReferencedEnrollmentIds(castIds);
  }
}
