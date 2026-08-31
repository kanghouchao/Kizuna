package com.kizuna.auth.application;

import org.springframework.security.authentication.BadCredentialsException;

/**
 * サービスID（本人種別 SERVICE）へのトークン発行を拒否したことを表す例外。
 *
 * <p>{@code BadCredentialsException} を継承するのは観測面を資格情報の不一致と同一にするため — 別の型・別の文言にすると、応答の違いが
 * 「その身分はサービスIDである」ことの存在オラクルになる。
 */
public class ServiceIdentityLoginException extends BadCredentialsException {

  public ServiceIdentityLoginException(String message) {
    super(message);
  }
}
