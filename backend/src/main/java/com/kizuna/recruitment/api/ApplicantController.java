package com.kizuna.recruitment.api;

import com.kizuna.recruitment.api.dto.ApplicantHistoryResponse;
import com.kizuna.recruitment.api.dto.ApplicantIntakeRequest;
import com.kizuna.recruitment.api.dto.ApplicantInterviewRequest;
import com.kizuna.recruitment.api.dto.ApplicantResponse;
import com.kizuna.recruitment.api.dto.ApplicantSummaryResponse;
import com.kizuna.recruitment.api.dto.ApplicantTransitionRequest;
import com.kizuna.recruitment.api.dto.ApplicantUpdateRequest;
import com.kizuna.recruitment.api.dto.RecruitmentPolicyResponse;
import com.kizuna.recruitment.application.ApplicantService;
import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.shared.storescope.CurrentStore;
import com.kizuna.shared.web.CursorPage;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/applicants")
@RequiredArgsConstructor
public class ApplicantController {
  private final ApplicantService applicants;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_RECRUITMENT_VIEW')")
  public Page<ApplicantSummaryResponse> list(
      @CurrentStore Long storeId,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) ApplicantStatus status,
      @PageableDefault(size = 20) Pageable pageable) {
    return applicants.list(storeId, search, status, pageable);
  }

  @GetMapping("/policy")
  @PreAuthorize("hasAuthority('PERM_RECRUITMENT_VIEW')")
  public RecruitmentPolicyResponse policy() {
    return RecruitmentPolicyResponse.unconfigured();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_RECRUITMENT_VIEW')")
  public ApplicantResponse get(@CurrentStore Long storeId, @PathVariable String id) {
    return applicants.get(storeId, id);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_RECRUITMENT_VIEW') and hasAuthority('PERM_RECRUITMENT_MANAGE')")
  public ResponseEntity<ApplicantResponse> create(
      @CurrentStore Long storeId,
      @Valid @RequestBody ApplicantIntakeRequest request,
      Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(applicants.create(storeId, request, principal.getName()));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_RECRUITMENT_VIEW') and hasAuthority('PERM_RECRUITMENT_MANAGE')")
  public ApplicantResponse update(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody ApplicantUpdateRequest request,
      Principal principal) {
    return applicants.update(storeId, id, request, principal.getName());
  }

  @PutMapping("/{id}/interview")
  @PreAuthorize("hasAuthority('PERM_RECRUITMENT_VIEW') and hasAuthority('PERM_RECRUITMENT_MANAGE')")
  public ApplicantResponse interview(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody ApplicantInterviewRequest request,
      Principal principal) {
    return applicants.interview(storeId, id, request, principal.getName());
  }

  @PostMapping("/{id}/transitions")
  @PreAuthorize("hasAuthority('PERM_RECRUITMENT_VIEW') and hasAuthority('PERM_RECRUITMENT_MANAGE')")
  public ApplicantResponse transition(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody ApplicantTransitionRequest request,
      Principal principal) {
    return applicants.transition(storeId, id, request, principal.getName());
  }

  @PostMapping("/{id}/decision")
  @PreAuthorize("hasAuthority('PERM_RECRUITMENT_VIEW') and hasAuthority('PERM_RECRUITMENT_DECIDE')")
  public ApplicantResponse decide(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody ApplicantTransitionRequest request,
      Principal principal) {
    return applicants.decide(storeId, id, request, principal.getName());
  }

  @GetMapping("/{id}/history")
  @PreAuthorize("hasAuthority('PERM_RECRUITMENT_VIEW')")
  public CursorPage<ApplicantHistoryResponse> history(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return applicants.history(storeId, id, cursor, size);
  }
}
