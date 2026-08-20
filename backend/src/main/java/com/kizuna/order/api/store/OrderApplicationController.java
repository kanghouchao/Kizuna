package com.kizuna.order.api.store;

import com.kizuna.order.api.dto.GuestOrderApplicationCreateRequest;
import com.kizuna.order.api.dto.GuestOrderApplicationResponse;
import com.kizuna.order.api.dto.OrderApplicationConfirmationRequest;
import com.kizuna.order.api.dto.OrderApplicationDeclineRequest;
import com.kizuna.order.api.dto.OrderApplicationResponse;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.application.GuestOrderApplicationService;
import com.kizuna.order.application.OrderService;
import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.order.infrastructure.GuestApplicationRateLimiter;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.TooManyRequestsException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.web.CursorPage;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 店舗の予約受付箱。申請（OrderApplication）の一覧・確定・謝絶を受け持つ（ADR 0017）。 */
@RestController
@RequestMapping("/store/order-applications")
@RequiredArgsConstructor
public class OrderApplicationController {

  private final OrderService orderService;
  private final GuestOrderApplicationService guestOrderApplicationService;
  private final GuestApplicationRateLimiter guestApplicationRateLimiter;
  private final StoreContext storeContext;

  /**
   * 公開店面からのゲスト予約申請を受け付ける。匿名で、店舗は店面 middleware が域名から解決してヘッダで運ぶ（{@code StoreIdInterceptor}）。
   *
   * <p>流量制限はサービスのトランザクション境界の外で判定する。撥ねる要求のためにトランザクションを開けない。
   *
   * <p>応答は受付番号だけで、申請の内容は返さない — 送った本人だけが読めることを保証する手立てがこの経路には無い。
   */
  @PostMapping("/public")
  @PermitAll
  public ResponseEntity<GuestOrderApplicationResponse> requestAsGuest(
      @Valid @RequestBody GuestOrderApplicationCreateRequest request,
      HttpServletRequest httpRequest) {
    if (!guestApplicationRateLimiter.tryConsume(storeContext.getStoreId(), originOf(httpRequest))) {
      throw new TooManyRequestsException("送信が続いたため受け付けられませんでした。しばらく時間をおいてからお試しください");
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(guestOrderApplicationService.request(request));
  }

  /**
   * 流量を数える発信元。読むのは {@code X-Forwarded-For} の<b>末尾</b>で、これが逆プロキシ自身が書いた直前の相手である。 手前の要素は client
   * が自由に書けるので、先頭を採ると計数の鍵を申請者側から選べてしまう。
   */
  private static String originOf(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded == null || forwarded.isBlank()) {
      return request.getRemoteAddr();
    }
    String[] hops = forwarded.split(",");
    return hops[hops.length - 1].trim();
  }

  /**
   * 予約申請の一覧。状態の群を指定してカーソルで辿る（受付箱は PENDING）。
   *
   * <p>続きは応答の {@code next_cursor} をそのまま {@code cursor} に渡して取る。確定・謝絶で行が消えていく一覧なので、
   * 位置を「何件目か」で指すと処理の直後に境界の申請を飛ばす。
   */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<CursorPage<OrderApplicationResponse>> list(
      @RequestParam(name = "statuses") Set<OrderApplicationStatus> statuses,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(orderService.listApplications(statuses, cursor, size));
  }

  /**
   * 予約申請を確定する — 確定内容で受注を CONFIRMED で生成し、申請行へ order_id を回写する。応答は生成された受注（201）。
   *
   * <p>確定は当店の台帳行と関連を自動で整えるため、同一会員の申請 2 件が同時に確定すると双方が「関連なし」を観測し、遅い側が関連の部分一意索引に敗れる。 敗者は取り直す —
   * 勝者の関連は既に commit されているので、2 度目の確定は自動整備の再利用の枝へ落ちて収束する。
   *
   * <p>この分岐はサービスのトランザクション境界の外に置かなければならない（ADR 0007 と同じ紀律）— 制約違反の時点で敗者のトランザクションは作廃されており、内側で catch
   * しても勝者の関連を読み直せない。取り直しは 1 度だけで、それでも敗れる場合（勝者の関連がその間に解除される等）は一意違反として 409
   * に落ちる。他の整合性違反は実装欠陥なのでそのまま上げる。
   */
  @PostMapping("/{id}/confirmation")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderResponse> confirm(
      @PathVariable String id,
      @Valid @RequestBody OrderApplicationConfirmationRequest request,
      Principal principal) {
    try {
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(orderService.confirmApplication(id, request, principal.getName()));
    } catch (DataIntegrityViolationException ex) {
      if (!IntegrityViolations.violates(
          ex, DbConstraint.UQ_T_CUSTOMER_MEMBER_LINKS_ACTIVE_MEMBER)) {
        throw ex;
      }
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(orderService.confirmApplication(id, request, principal.getName()));
    }
  }

  /** 予約申請を謝絶する。理由は必須で、実行者と時刻が記録に残る。確定後の取消は受注側の専用操作が受け持つ。 */
  @PostMapping("/{id}/refusal")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<Void> decline(
      @PathVariable String id,
      @Valid @RequestBody OrderApplicationDeclineRequest request,
      Principal principal) {
    orderService.declineApplication(id, request, principal.getName());
    return ResponseEntity.noContent().build();
  }
}
