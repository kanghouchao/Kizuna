package com.kizuna.point.api.me;

import com.kizuna.point.api.dto.MemberPointBalanceResponse;
import com.kizuna.point.api.dto.MemberPointEntryResponse;
import com.kizuna.point.application.MemberPointService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本人（会員）ポータルのポイント API。店舗文脈を要さない経路で、隔離は本人の一致による（{@code MemberPointService}）。 */
@RestController
@RequestMapping("/me/points")
@RequiredArgsConstructor
public class MemberPointController {

  private final MemberPointService memberPointService;

  @GetMapping("/balance")
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<MemberPointBalanceResponse> balance(Principal principal) {
    return ResponseEntity.ok(memberPointService.balance(principal.getName()));
  }

  @GetMapping("/entries")
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<Page<MemberPointEntryResponse>> entries(
      Principal principal,
      @PageableDefault(
              size = 20,
              sort = {"createdAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(memberPointService.entries(principal.getName(), pageable));
  }
}
