package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.customer.api.dto.CustomerMergeHistoryResponse;
import com.kizuna.customer.api.dto.CustomerMergeResponse;
import com.kizuna.customer.api.dto.MergeDirection;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerMerge;
import com.kizuna.customer.domain.CustomerMergeRepository;
import com.kizuna.customer.domain.CustomerMergeView;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

/**
 * 顧客統合の拒否条件と、付替え・墓標・履歴の書き込み順をサービス層で固定する。
 *
 * <p>複数行の照合・部分一意索引・行ロックの実効はモックでは守れないので、そこは {@code CustomerMergeIT}（実 PostgreSQL）に委ね、ここでは 二重管理にしない。
 */
@ExtendWith(MockitoExtension.class)
class CustomerMergeServiceTest {

  private static final String ACTOR_EMAIL = "manager@kizuna.test";
  private static final long ACTOR_ID = 42L;
  private static final long STORE_ID = 1L;

  /** 昇順で押さえることを見るため、存続行のほうが辞書順で後になる組にする。 */
  private static final String SURVIVING_ID = "c-2";

  private static final String MERGED_ID = "c-1";

  private static final OffsetDateTime MERGED_AT = OffsetDateTime.parse("2026-08-10T10:00:00+09:00");

  @Mock private CustomerRepository customerRepository;
  @Mock private CustomerMemberLinkRepository customerMemberLinkRepository;
  @Mock private CustomerMergeRepository customerMergeRepository;
  @Mock private PlatformUserRepository platformUserRepository;
  @Mock private StoreContext storeContext;

  @InjectMocks private CustomerMergeService service;

  @Test
  @DisplayName("同じ顧客同士の統合は 400 系で拒まれ、行を一切押さえないこと")
  void rejectsSelfMerge() {
    assertThatThrownBy(() -> service.merge(SURVIVING_ID, SURVIVING_ID, ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class);

    Mockito.verify(customerRepository, Mockito.never()).findByIdForUpdate(Mockito.anyString());
  }

  @Test
  @DisplayName("押さえられない顧客は他店舗も不在も区別なく 404 になること")
  void rejectsUnknownCustomer() {
    givenActor();
    Mockito.when(customerRepository.findByIdForUpdate(MERGED_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.merge(SURVIVING_ID, MERGED_ID, ACTOR_EMAIL))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("2 行は顧客 ID の昇順で押さえられること（同じ 2 行を逆向きに統合する要求とデッドロックしない）")
  void locksBothRowsInAscendingIdOrder() {
    givenActor();
    givenBothRowsLocked();
    givenNeitherIsMerged();
    givenNoActiveLinks();
    givenStore();

    service.merge(SURVIVING_ID, MERGED_ID, ACTOR_EMAIL);

    InOrder inOrder = Mockito.inOrder(customerRepository);
    inOrder.verify(customerRepository).findByIdForUpdate(MERGED_ID);
    inOrder.verify(customerRepository).findByIdForUpdate(SURVIVING_ID);
  }

  @Test
  @DisplayName("存続行に墓標を指定した統合は、2 本目のロックを待つ前に 409 で拒まれること")
  void rejectsMergingIntoATombstone() {
    givenActor();
    givenFirstRowLocked();
    Mockito.when(customerRepository.isMerged(SURVIVING_ID)).thenReturn(true);

    assertThatThrownBy(() -> service.merge(SURVIVING_ID, MERGED_ID, ACTOR_EMAIL))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("統合済み");

    // 参照の解決は墓標 → 統合先の順に押さえるので、墓標を名指した要求がここで 2 本目を待つと
    // ID 昇順と逆向きの待ちが環になる
    Mockito.verify(customerRepository, Mockito.never()).findByIdForUpdate(SURVIVING_ID);
    verifyNothingWasMoved();
  }

  @Test
  @DisplayName("既に墓標の行を再度統合しようとすると 409 で拒まれること（応答喪失後の再送が台帳を壊さない）")
  void rejectsMergingATombstoneAgain() {
    givenActor();
    givenFirstRowLocked();
    Mockito.when(customerRepository.isMerged(SURVIVING_ID)).thenReturn(false);
    Mockito.when(customerRepository.isMerged(MERGED_ID)).thenReturn(true);

    assertThatThrownBy(() -> service.merge(SURVIVING_ID, MERGED_ID, ACTOR_EMAIL))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("統合済み");

    Mockito.verify(customerRepository, Mockito.never()).findByIdForUpdate(SURVIVING_ID);
    verifyNothingWasMoved();
  }

  @Test
  @DisplayName("両行が ACTIVE 関連を持つ統合は 409 で拒まれ、先に関連を解除することが応答から判ること")
  void rejectsWhenBothRowsCarryAnActiveLink() {
    givenActor();
    givenBothRowsLocked();
    givenNeitherIsMerged();
    Mockito.when(
            customerMemberLinkRepository.existsByCustomerIdAndStatus(
                SURVIVING_ID, LinkStatus.ACTIVE))
        .thenReturn(true);
    Mockito.when(
            customerMemberLinkRepository.existsByCustomerIdAndStatus(MERGED_ID, LinkStatus.ACTIVE))
        .thenReturn(true);

    assertThatThrownBy(() -> service.merge(SURVIVING_ID, MERGED_ID, ACTOR_EMAIL))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("先に関連を解除");

    verifyNothingWasMoved();
  }

  @Test
  @DisplayName("片方だけが ACTIVE 関連を持つ統合は通ること（拒否は「両行とも」のときだけ）")
  void allowsMergeWhenOnlyOneRowCarriesAnActiveLink() {
    givenActor();
    givenBothRowsLocked();
    givenNeitherIsMerged();
    Mockito.when(
            customerMemberLinkRepository.existsByCustomerIdAndStatus(
                SURVIVING_ID, LinkStatus.ACTIVE))
        .thenReturn(true);
    Mockito.when(
            customerMemberLinkRepository.existsByCustomerIdAndStatus(MERGED_ID, LinkStatus.ACTIVE))
        .thenReturn(false);
    givenStore();

    assertThat(service.merge(SURVIVING_ID, MERGED_ID, ACTOR_EMAIL).survivingCustomerId())
        .isEqualTo(SURVIVING_ID);
  }

  @Test
  @DisplayName("統合履歴には一括 UPDATE が報告した件数がそのまま残り、実行者と統合の両行が記録されること")
  void recordsTheMergeWithTheCountsReportedByTheBulkUpdates() {
    givenActor();
    givenBothRowsLocked();
    givenNeitherIsMerged();
    givenNoActiveLinks();
    givenStore();
    Mockito.when(customerMergeRepository.repointOrders(SURVIVING_ID, MERGED_ID, STORE_ID))
        .thenReturn(7);
    Mockito.when(customerMemberLinkRepository.repointCustomer(SURVIVING_ID, MERGED_ID, STORE_ID))
        .thenReturn(3);

    CustomerMergeResponse response = service.merge(SURVIVING_ID, MERGED_ID, ACTOR_EMAIL);

    assertThat(response.movedOrderCount()).isEqualTo(7);
    assertThat(response.movedLinkCount()).isEqualTo(3);
    ArgumentCaptor<CustomerMerge> recorded = ArgumentCaptor.forClass(CustomerMerge.class);
    Mockito.verify(customerMergeRepository).save(recorded.capture());
    assertThat(recorded.getValue().getSurvivingCustomerId()).isEqualTo(SURVIVING_ID);
    assertThat(recorded.getValue().getMergedCustomerId()).isEqualTo(MERGED_ID);
    assertThat(recorded.getValue().getMergedBy()).isEqualTo(ACTOR_ID);
    assertThat(recorded.getValue().getMergedAt()).isNotNull();
    assertThat(recorded.getValue().getMovedOrderCount()).isEqualTo(7);
    assertThat(recorded.getValue().getMovedLinkCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("連鎖の圧平は被統合行を墓標にする前に走ること（自分自身を圧平の対象にしない）")
  void flattensExistingTombstonesBeforeTurningTheMergedRowIntoOne() {
    givenActor();
    Customer merged = givenBothRowsLocked();
    givenNeitherIsMerged();
    givenNoActiveLinks();
    givenStore();

    service.merge(SURVIVING_ID, MERGED_ID, ACTOR_EMAIL);

    InOrder inOrder = Mockito.inOrder(customerRepository);
    inOrder.verify(customerRepository).flattenMergedInto(SURVIVING_ID, MERGED_ID, STORE_ID);
    inOrder.verify(customerRepository).save(merged);
    assertThat(merged.getMergedIntoId()).isEqualTo(SURVIVING_ID);
  }

  // ==================== 統合履歴の読み ====================

  @Test
  @DisplayName("履歴の向きと相手は、問い合わせた顧客がどちら側かで決まること")
  void resolvesTheDirectionAndCounterpartAgainstTheRequestedCustomer() {
    HistoryRow row = new HistoryRow("m-1", SURVIVING_ID, MERGED_ID, MERGED_AT);
    givenCustomerExists(SURVIVING_ID);
    givenCustomerExists(MERGED_ID);
    Mockito.when(customerMergeRepository.findHistory(Mockito.anyString(), Mockito.any()))
        .thenReturn(List.of(row));

    CustomerMergeHistoryResponse fromSurviving =
        service.history(SURVIVING_ID, null, 20).content().get(0);
    CustomerMergeHistoryResponse fromMerged = service.history(MERGED_ID, null, 20).content().get(0);

    assertThat(fromSurviving.direction()).isEqualTo(MergeDirection.SURVIVING);
    assertThat(fromSurviving.counterpartCustomerId()).isEqualTo(MERGED_ID);
    assertThat(fromSurviving.counterpartCustomerName()).isEqualTo(MERGED_ID + "-名");
    // 同じ 1 件が、被統合行から見ると反対向きで、相手も入れ替わる
    assertThat(fromMerged.direction()).isEqualTo(MergeDirection.MERGED);
    assertThat(fromMerged.counterpartCustomerId()).isEqualTo(SURVIVING_ID);
    assertThat(fromMerged.counterpartCustomerName()).isEqualTo(SURVIVING_ID + "-名");
    assertThat(fromMerged.id()).isEqualTo(fromSurviving.id());
    assertThat(fromMerged.movedOrderCount()).isEqualTo(2);
    assertThat(fromMerged.movedLinkCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("統合の無い顧客は空の一覧で返り、404 にならないこと")
  void returnsAnEmptyPageInsteadOfNotFoundWhenThereAreNoMerges() {
    givenCustomerExists(SURVIVING_ID);
    Mockito.when(customerMergeRepository.findHistory(Mockito.anyString(), Mockito.any()))
        .thenReturn(List.of());

    CursorPage<CustomerMergeHistoryResponse> page = service.history(SURVIVING_ID, null, 20);

    assertThat(page.content()).isEmpty();
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  @DisplayName("引けない顧客の履歴は 404 になり、統合の記録を読みに行かないこと")
  void refusesToReadTheHistoryOfACustomerItCannotSee() {
    Mockito.when(customerRepository.existsById(SURVIVING_ID)).thenReturn(false);

    assertThatThrownBy(() -> service.history(SURVIVING_ID, null, 20))
        .isInstanceOf(NotFoundException.class);

    Mockito.verify(customerMergeRepository, Mockito.never())
        .findHistory(Mockito.anyString(), Mockito.any());
  }

  @Test
  @DisplayName("上限より 1 件多く取り、余分は返さずに続きの位置だけを組むこと")
  void fetchesOneExtraRowAndTurnsItIntoTheNextCursor() {
    HistoryRow first = new HistoryRow("m-2", SURVIVING_ID, MERGED_ID, MERGED_AT);
    HistoryRow last = new HistoryRow("m-1", SURVIVING_ID, "c-0", MERGED_AT.minusDays(1));
    givenCustomerExists(SURVIVING_ID);
    Mockito.when(customerMergeRepository.findHistory(Mockito.anyString(), Mockito.any()))
        .thenReturn(List.of(first, last));

    CursorPage<CustomerMergeHistoryResponse> page = service.history(SURVIVING_ID, null, 1);

    ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);
    Mockito.verify(customerMergeRepository).findHistory(Mockito.eq(SURVIVING_ID), limit.capture());
    assertThat(limit.getValue().max()).isEqualTo(2);
    assertThat(page.content()).hasSize(1);
    // 続きの位置は返した最後の行から組む。余分の 1 件から組むと、その行が次の頁で消える
    assertThat(PageCursor.decode(page.nextCursor()))
        .isEqualTo(new PageCursor(MERGED_AT.toString(), "m-2"));
  }

  @Test
  @DisplayName("続きの位置は復号した組のまま問い合わせへ渡ること")
  void passesTheDecodedCursorStraightToTheQuery() {
    givenCustomerExists(SURVIVING_ID);
    Mockito.when(
            customerMergeRepository.findHistoryAfter(
                Mockito.anyString(), Mockito.any(), Mockito.anyString(), Mockito.any()))
        .thenReturn(List.of());

    service.history(SURVIVING_ID, new PageCursor(MERGED_AT.toString(), "m-9").encode(), 20);

    Mockito.verify(customerMergeRepository)
        .findHistoryAfter(
            Mockito.eq(SURVIVING_ID), Mockito.eq(MERGED_AT), Mockito.eq("m-9"), Mockito.any());
  }

  // ==================== 前提の組み立て ====================

  /** 読み側 projection の代役。向きの判定と相手の取り出しを見るので、件数と実行者名は固定でよい。 */
  private record HistoryRow(
      String id, String survivingCustomerId, String mergedCustomerId, OffsetDateTime mergedAt)
      implements CustomerMergeView {

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getSurvivingCustomerId() {
      return survivingCustomerId;
    }

    @Override
    public String getMergedCustomerId() {
      return mergedCustomerId;
    }

    @Override
    public String getSurvivingCustomerName() {
      return survivingCustomerId + "-名";
    }

    @Override
    public String getMergedCustomerName() {
      return mergedCustomerId + "-名";
    }

    @Override
    public String getMergedByName() {
      return "田中花子";
    }

    @Override
    public OffsetDateTime getMergedAt() {
      return mergedAt;
    }

    @Override
    public int getMovedOrderCount() {
      return 2;
    }

    @Override
    public int getMovedLinkCount() {
      return 1;
    }
  }

  private void givenCustomerExists(String customerId) {
    Mockito.lenient().when(customerRepository.existsById(customerId)).thenReturn(true);
  }

  private void givenActor() {
    PlatformUser actor =
        PlatformUser.builder()
            .email(ACTOR_EMAIL)
            .password("encoded")
            .displayName("田中花子")
            .enabled(true)
            .userType(UserType.STAFF)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(STORE_ID))
            .roleIds(Set.of(1L))
            .build();
    actor.setId(ACTOR_ID);
    Mockito.when(platformUserRepository.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.of(actor));
  }

  /** 両行が押さえられた状態。戻り値は被統合行の実体（墓標化の宛先）。 */
  /** 昇順の 1 本目（被統合行）だけを押さえられる状態。墓標の判定で 2 本目より前に撥ねる経路で使う。 */
  private void givenFirstRowLocked() {
    Mockito.when(customerRepository.findByIdForUpdate(MERGED_ID))
        .thenReturn(Optional.of(Customer.builder().build()));
  }

  private Customer givenBothRowsLocked() {
    Customer merged = Customer.builder().build();
    Mockito.when(customerRepository.findByIdForUpdate(MERGED_ID)).thenReturn(Optional.of(merged));
    Mockito.when(customerRepository.findByIdForUpdate(SURVIVING_ID))
        .thenReturn(Optional.of(Customer.builder().build()));
    return merged;
  }

  private void givenNeitherIsMerged() {
    Mockito.when(customerRepository.isMerged(SURVIVING_ID)).thenReturn(false);
    Mockito.when(customerRepository.isMerged(MERGED_ID)).thenReturn(false);
  }

  private void givenNoActiveLinks() {
    Mockito.when(
            customerMemberLinkRepository.existsByCustomerIdAndStatus(
                SURVIVING_ID, LinkStatus.ACTIVE))
        .thenReturn(false);
  }

  private void givenStore() {
    Mockito.when(storeContext.getStoreId()).thenReturn(STORE_ID);
  }

  private void verifyNothingWasMoved() {
    Mockito.verify(customerMergeRepository, Mockito.never())
        .repointOrders(Mockito.anyString(), Mockito.anyString(), Mockito.anyLong());
    Mockito.verify(customerMemberLinkRepository, Mockito.never())
        .repointCustomer(Mockito.anyString(), Mockito.anyString(), Mockito.anyLong());
    Mockito.verify(customerRepository, Mockito.never())
        .flattenMergedInto(Mockito.anyString(), Mockito.anyString(), Mockito.anyLong());
    Mockito.verify(customerMergeRepository, Mockito.never()).save(Mockito.any());
  }
}
