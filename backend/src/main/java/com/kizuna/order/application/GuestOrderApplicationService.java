package com.kizuna.order.application;

import com.kizuna.order.api.dto.GuestOrderApplicationCreateRequest;
import com.kizuna.order.api.dto.GuestOrderApplicationResponse;
import com.kizuna.order.domain.OrderApplication;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 公開店面からのゲスト予約申請ユースケース。会員の申請と同じ受け皿（OrderApplication）の PENDING として起きる（ADR 0017）。
 *
 * <p>店舗は訪問された域名から店面 middleware が解決してヘッダで運ぶため、この経路は店舗文脈を持つ。
 * 申請本体に店舗を自称させないのは、店舗定位の根拠を「訪問された域名」一本に保つため。
 *
 * <p>申請者は匿名で、会員コードのスナップショットを持たない。この不在がそのまま「ゲスト申請である」ことの印になり、 確定時の受付経路（GUEST_WEB）と顧客の決め方を分ける（{@link
 * OrderApplication#isGuest()}）。
 */
@Service
@RequiredArgsConstructor
public class GuestOrderApplicationService {

  private final OrderApplicationRepository orderApplicationRepository;
  private final OrderApplicationIntake orderApplicationIntake;
  private final StoreContext storeContext;

  /** 予約を申請する。予約申請の PENDING 行だけが起き、受注（t_orders）には行が生まれない。 */
  @StoreScoped
  @Transactional
  public GuestOrderApplicationResponse request(GuestOrderApplicationCreateRequest request) {
    Long storeId = storeContext.getStoreId();
    orderApplicationIntake.validateRequestedVisit(
        storeId, request.getBusinessDate(), request.getCastId());

    // store_id は StoreScopeStampListener が @PrePersist で採番する
    OrderApplication application =
        OrderApplication.builder()
            .status(OrderApplicationStatus.PENDING)
            .businessDate(request.getBusinessDate())
            .arrivalScheduledStartTime(request.getArrivalScheduledStartTime())
            .pax(request.getPax())
            .castId(request.getCastId())
            .remarks(request.getRemarks())
            .contactName(request.getContactName())
            .contactPhoneNumber(request.getContactPhoneNumber())
            .build();

    return new GuestOrderApplicationResponse(orderApplicationRepository.save(application).getId());
  }
}
