package com.kizuna.memberconsole.api;

import com.kizuna.member.application.MemberStatusFilter;
import com.kizuna.memberconsole.api.dto.MemberDetailResponse;
import com.kizuna.memberconsole.api.dto.MemberLedgerEntryResponse;
import com.kizuna.memberconsole.api.dto.MemberSummaryResponse;
import com.kizuna.memberconsole.api.dto.StaffWithdrawalRequest;
import com.kizuna.memberconsole.application.MemberConsoleService;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.shared.web.JwtClaims;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 職員の会員コンソール API。照会は MEMBER_VIEW、代行退会は MEMBER_WITHDRAW。同じ経路の匿名 POST（自助登録）は {@code
 * MemberRegistrationController} が持つ。
 */
@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberConsoleController {

  private final MemberConsoleService memberConsoleService;
  private final StoreScopeHolder storeScopeHolder;

  /** 状態の既定は有効（未退会）。検索語は会員コード・電子メール・表示名の部分一致。並びは登録の新しい順に固定。 */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_MEMBER_VIEW')")
  public ResponseEntity<Page<MemberSummaryResponse>> list(
      @RequestParam(defaultValue = "ACTIVE") MemberStatusFilter status,
      @RequestParam(required = false) String search,
      Pageable pageable) {
    return ResponseEntity.ok(memberConsoleService.list(status, search, pageable));
  }

  /** 関連の展開範囲は閲覧者の店舗集合で決まるので、要求ごとに解決した集合を渡す（解決できなければ 403）。 */
  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_MEMBER_VIEW')")
  public ResponseEntity<MemberDetailResponse> get(@PathVariable Long id) {
    StoreScope scope = storeScopeHolder.current();
    if (scope == null) throw new AccessDeniedException("店舗集合スコープを解決できません");
    return ResponseEntity.ok(memberConsoleService.detail(id, scope));
  }

  @GetMapping("/{id}/point-entries")
  @PreAuthorize("hasAuthority('PERM_MEMBER_VIEW')")
  public ResponseEntity<Page<MemberLedgerEntryResponse>> pointEntries(
      @PathVariable Long id,
      @PageableDefault(
              size = 20,
              sort = {"createdAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(memberConsoleService.entries(id, pageable));
  }

  /** 代行退会。操作者は検証済みトークンの利用者 ID。二度目は 409、存在しない会員は 404。 */
  @PostMapping("/{id}/withdrawal")
  @PreAuthorize("hasAuthority('PERM_MEMBER_WITHDRAW')")
  public ResponseEntity<Void> withdraw(
      @PathVariable Long id,
      Principal principal,
      @Valid @RequestBody StaffWithdrawalRequest request) {
    memberConsoleService.withdraw(id, JwtClaims.userIdOf(principal), request.getReason());
    return ResponseEntity.noContent().build();
  }
}
