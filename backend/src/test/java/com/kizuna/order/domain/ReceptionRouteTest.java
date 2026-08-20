package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 受付経路の値域と、店舗が名乗ってよい経路の線引き。 */
class ReceptionRouteTest {

  @Test
  @DisplayName("値域が三つのままであること（値を足した人にここで店舗の可否を決めさせる）")
  void theValueSetIsPinned() {
    // 実装は許す値を数え上げる形なので、新しい値は既定で拒否側へ倒れる。倒れる先が安全でも
    // 正しいとは限らない（店頭受付のような値は名乗らせるべきかもしれない）ため、値域自体を
    // 固定して、足した人が下の可否表を書き足すまで赤にする。
    assertThat(ReceptionRoute.values())
        .containsExactly(ReceptionRoute.MEMBER_WEB, ReceptionRoute.GUEST_WEB, ReceptionRoute.PHONE);
  }

  @Test
  @DisplayName("店舗が名乗れる経路が電話受付だけであること")
  void onlyPhoneIsSelectableByTheStore() {
    Set<ReceptionRoute> selectable =
        Arrays.stream(ReceptionRoute.values())
            .filter(ReceptionRoute::isStoreSelectable)
            .collect(Collectors.toSet());

    assertThat(selectable).containsExactly(ReceptionRoute.PHONE);
  }

  @Test
  @DisplayName("Web 申請の経路は店舗の作成契約から締め出されていること")
  void webApplicationRoutesAreNotSelectable() {
    assertThat(ReceptionRoute.MEMBER_WEB.isStoreSelectable()).isFalse();
    assertThat(ReceptionRoute.GUEST_WEB.isStoreSelectable()).isFalse();
  }
}
