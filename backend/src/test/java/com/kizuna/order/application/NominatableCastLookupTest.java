package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

/**
 * 指名先として成立するかの判定そのものを固定するテスト。
 *
 * <p>会員の申請・店舗の受注作成・汎用更新・申請編集・確定と候補一覧が共有する述語なので、条件の判定はここが唯一の証跡になる。 呼び出し側（{@link OrderService} /
 * {@link MemberOrderService}）のテストは「空が返ったとき何を投げるか」の翻訳だけを固定する。
 */
@ExtendWith(MockitoExtension.class)
class NominatableCastLookupTest {

  private static final long STORE_ID = 1L;
  private static final long OTHER_STORE_ID = 2L;

  @Mock CastRepository castRepository;

  @InjectMocks NominatableCastLookup lookup;

  private static Cast cast(String id, long storeId, String status) {
    Cast cast = Cast.builder().name("キャスト" + id).status(status).build();
    cast.setId(id);
    cast.setStoreId(storeId);
    return cast;
  }

  @Test
  @DisplayName("当店に在籍中のキャストは指名先として成立すること")
  void findReturnsActiveCastOfTheStore() {
    Cast active = cast("cast-1", STORE_ID, "ACTIVE");
    when(castRepository.findById("cast-1")).thenReturn(Optional.of(active));

    assertThat(lookup.find(STORE_ID, "cast-1")).contains(active);
  }

  @Test
  @DisplayName("他店舗のキャストは成立しないこと")
  void findRejectsCastOfAnotherStore() {
    // 店舗の一致を述語に置くのは、キャストの読み取りに掛かる絞り込みへ暗黙に頼らないため
    when(castRepository.findById("cast-1"))
        .thenReturn(Optional.of(cast("cast-1", OTHER_STORE_ID, "ACTIVE")));

    assertThat(lookup.find(STORE_ID, "cast-1")).isEmpty();
  }

  @Test
  @DisplayName("在籍停止のキャストは成立しないこと")
  void findRejectsSuspendedCast() {
    when(castRepository.findById("cast-1"))
        .thenReturn(Optional.of(cast("cast-1", STORE_ID, "INACTIVE")));

    assertThat(lookup.find(STORE_ID, "cast-1")).isEmpty();
  }

  @Test
  @DisplayName("在籍状態が未設定のキャストは成立しないこと")
  void findRejectsCastWithoutStatus() {
    // API 経由の作成はマッパの既定値で在籍中になるが、既定値は API 経路だけの話。未設定を通すと
    // 「在籍中だけを指名できる」が静かに崩れる
    when(castRepository.findById("cast-1")).thenReturn(Optional.of(cast("cast-1", STORE_ID, null)));

    assertThat(lookup.find(STORE_ID, "cast-1")).isEmpty();
  }

  @Test
  @DisplayName("存在しないキャストは成立しないこと")
  void findReturnsEmptyWhenCastIsMissing() {
    when(castRepository.findById("nope")).thenReturn(Optional.empty());

    assertThat(lookup.find(STORE_ID, "nope")).isEmpty();
  }

  @Test
  @DisplayName("候補は当店の在籍中に絞り、全順序の並びで上限まで引くこと")
  void searchCandidatesAsksForActiveCastsOfTheStoreInATotalOrder() {
    Cast candidate = cast("cast-1", STORE_ID, "ACTIVE");
    when(castRepository
            .findByStoreIdAndStatusAndNameContainingIgnoreCaseOrderByDisplayOrderAscIdAsc(
                STORE_ID, "ACTIVE", "花", Limit.of(10)))
        .thenReturn(List.of(candidate));

    assertThat(lookup.searchCandidates(STORE_ID, "花")).containsExactly(candidate);
  }

  @Test
  @DisplayName("検索語なしは絞り込みなしとして先頭から引くこと")
  void searchCandidatesTreatsAMissingKeywordAsNoFilter() {
    // コンボボックスは開いた時点で語なしに一度取りに行く。ここで空を返すと候補が何も出ない
    when(castRepository
            .findByStoreIdAndStatusAndNameContainingIgnoreCaseOrderByDisplayOrderAscIdAsc(
                STORE_ID, "ACTIVE", "", Limit.of(10)))
        .thenReturn(List.of());

    assertThat(lookup.searchCandidates(STORE_ID, null)).isEmpty();

    verify(castRepository)
        .findByStoreIdAndStatusAndNameContainingIgnoreCaseOrderByDisplayOrderAscIdAsc(
            STORE_ID, "ACTIVE", "", Limit.of(10));
  }

  @Test
  @DisplayName("検索語の前後の空白は落として引くこと")
  void searchCandidatesTrimsTheKeyword() {
    when(castRepository
            .findByStoreIdAndStatusAndNameContainingIgnoreCaseOrderByDisplayOrderAscIdAsc(
                STORE_ID, "ACTIVE", "花", Limit.of(10)))
        .thenReturn(List.of());

    lookup.searchCandidates(STORE_ID, "  花  ");

    verify(castRepository)
        .findByStoreIdAndStatusAndNameContainingIgnoreCaseOrderByDisplayOrderAscIdAsc(
            STORE_ID, "ACTIVE", "花", Limit.of(10));
  }
}
