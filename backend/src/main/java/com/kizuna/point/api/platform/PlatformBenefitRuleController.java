package com.kizuna.point.api.platform;

import com.kizuna.point.api.dto.BenefitRuleCreateRequest;
import com.kizuna.point.api.dto.BenefitRuleDeactivationRequest;
import com.kizuna.point.api.dto.BenefitRuleResponse;
import com.kizuna.point.api.dto.BenefitRuleSummaryResponse;
import com.kizuna.point.api.dto.BenefitRuleUpdateRequest;
import com.kizuna.point.application.BenefitRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 特典規則の管理 API。全操作 BENEFIT_MANAGE 権限限定で、削除の口は無い（退場は停用で表す）。 */
@RestController
@RequestMapping("/platform/benefit-rules")
@RequiredArgsConstructor
public class PlatformBenefitRuleController {

  private final BenefitRuleService benefitRuleService;

  /** 一覧は適用店舗を件数まで畳んだ要約。店舗 ID の列挙が要る編集フォームは {@link #get(Long)} で個別に取得する。 */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_BENEFIT_MANAGE')")
  public ResponseEntity<Page<BenefitRuleSummaryResponse>> list(Pageable pageable) {
    return ResponseEntity.ok(benefitRuleService.list(pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_BENEFIT_MANAGE')")
  public ResponseEntity<BenefitRuleResponse> get(@PathVariable Long id) {
    return ResponseEntity.ok(benefitRuleService.get(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_BENEFIT_MANAGE')")
  public ResponseEntity<BenefitRuleResponse> create(
      @Valid @RequestBody BenefitRuleCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(benefitRuleService.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_BENEFIT_MANAGE')")
  public ResponseEntity<BenefitRuleResponse> update(
      @PathVariable Long id, @Valid @RequestBody BenefitRuleUpdateRequest request) {
    return ResponseEntity.ok(benefitRuleService.update(id, request));
  }

  /** 停用（退場）。再開の口は無く、二度目は 400 で撥ねる。確認した版と現物がずれていれば 409。 */
  @PostMapping("/{id}/deactivation")
  @PreAuthorize("hasAuthority('PERM_BENEFIT_MANAGE')")
  public ResponseEntity<Void> deactivate(
      @PathVariable Long id, @Valid @RequestBody BenefitRuleDeactivationRequest request) {
    benefitRuleService.deactivate(id, request);
    return ResponseEntity.noContent().build();
  }
}
