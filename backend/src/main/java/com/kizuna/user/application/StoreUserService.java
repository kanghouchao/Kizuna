package com.kizuna.user.application;

import com.kizuna.user.api.dto.StoreUserMeResponse;

/** 店舗ユーザー自身のアカウント操作サービス。 */
public interface StoreUserService {

  /**
   * ログイン中ユーザーの情報を返す。
   *
   * @param email 対象ユーザーのメールアドレス（Principal 名）
   */
  StoreUserMeResponse me(String email);

  /**
   * ログイン中ユーザーのニックネームを変更する。
   *
   * @param email 対象ユーザーのメールアドレス（Principal 名）
   * @param nickname 新しいニックネーム
   */
  StoreUserMeResponse updateProfile(String email, String nickname);
}
