package com.kizuna.auth.application;

import com.kizuna.point.application.BenefitGrantService;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 認証成功をログイン特典の発火事象として生産者へ渡す引き金。パスワード・LINE の両経路が同じ契機で引く。
 *
 * <p>付与は認証と別取引で、失敗は記録して認証を通す — 特典の不具合で誰もログインできなくなる方が損失が大きい。 吞む側を生産者と別の bean に置くのは、同じ bean 内の自己呼出しでは
 * REQUIRES_NEW の代理が掛からないため。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginBenefitTrigger {

  private final BenefitGrantService benefitGrantService;

  /** 認証成功 1 回につき 1 度呼ぶ。トークン更新・再発行・登録直後の自動ログインは呼ばない。 */
  public void onAuthenticated(User user) {
    if (user.getUserType() != UserType.MEMBER) {
      return;
    }
    try {
      benefitGrantService.grantLoginBenefits(user.getId());
    } catch (RuntimeException ex) {
      log.error("ログイン特典の付与に失敗しました（認証は成功） userId={}", user.getId(), ex);
    }
  }
}
