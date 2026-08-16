package com.kizuna.order.api.platform;

import com.kizuna.order.api.dto.MemberReceiptClaimRequest;
import com.kizuna.order.api.dto.MemberReceiptClaimResponse;
import com.kizuna.order.application.MemberReceiptClaimService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人（会員）ポータルの伝票トークン申領 API。店舗文脈を要さない経路で、帰属先は認証主体本人に固定される。
 *
 * <p>照会だけの読み口は持たない。「そのトークンが有効か」を答える口は、総当たりに対して当たり判定を返す口でもある。 申領の成否だけが唯一の応答で、失敗はすべて同形になる（{@code
 * MemberReceiptClaimService}）。
 */
@RestController
@RequestMapping("/platform/me/receipts")
@RequiredArgsConstructor
public class PlatformMemberReceiptController {

  private final MemberReceiptClaimService memberReceiptClaimService;

  /** 申領は本人の帰属記録を起こす生成なので 201。トークンは本体で受ける（パスに載せるとアクセスログへ残る）。 */
  @PostMapping
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<MemberReceiptClaimResponse> claim(
      Principal principal, @Valid @RequestBody MemberReceiptClaimRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(memberReceiptClaimService.claim(principal.getName(), request.getToken()));
  }
}
