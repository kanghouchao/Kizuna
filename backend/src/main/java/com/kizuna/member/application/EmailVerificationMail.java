package com.kizuna.member.application;

import com.kizuna.member.domain.EmailVerificationToken;
import com.kizuna.member.domain.EmailVerificationTokenRepository;
import com.kizuna.notification.transport.ContactTransport;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.persistence.Timestamps;
import com.kizuna.user.domain.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 検証メールの発行と送信（ADR 0040）。トークンは呼び出し側の取引で発行し、メールはコミット後に送信手段を直接呼んで送る。
 *
 * <p>通知の送信正本は通さない — 本文に秘密（トークン）が載り、正本の本文は職員が閲覧・再送できるため。送信の失敗（未設定・拒否・結果不明・例外）は すべてここで飲み込む。{@code
 * afterCommit()} の例外は呼び出し側へ伝播し、確定済みの登録を 500 に変えてしまう。
 */
@Log4j2
@Component
public class EmailVerificationMail {

  static final String SUBJECT = "メールアドレスの確認のお願い";
  static final String CONFIRMATION_PATH = "/auth/verify-email#";

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final EmailVerificationTokenRepository tokenRepository;
  private final ContactTransport mail;
  private final AppProperties appProperties;

  public EmailVerificationMail(
      EmailVerificationTokenRepository tokenRepository,
      List<ContactTransport> transports,
      AppProperties appProperties) {
    this.tokenRepository = tokenRepository;
    this.mail =
        transports.stream()
            .filter(transport -> transport.type() == ContactTransport.Channel.EMAIL)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("メールの送信手段がありません"));
    this.appProperties = appProperties;
  }

  /** 利用者の現在の宛先へトークンを発行し、コミット後に検証メールを送る。取引の外では呼べない。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void issue(User user) {
    String token = randomToken();
    tokenRepository.save(
        EmailVerificationToken.issueFor(
            user.getId(), digest(token), user.getEmail(), Timestamps.now()));
    Long userId = user.getId();
    String recipient = user.getEmail();
    String body = body(token);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            send(userId, recipient, body);
          }
        });
  }

  /** 照合に使うダイジェスト（SHA-256 の 16 進）。 */
  static String digest(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 は JCA の必須アルゴリズム。ここへ来るのは実行環境の欠陥。
      throw new IllegalStateException(e);
    }
  }

  // 宛先と本文はログに出さない（本文にはトークンが載る）。例外も型名だけを残す — 送信基盤の例外文言は宛先を含みうる。
  private void send(Long userId, String recipient, String body) {
    try {
      ContactTransport.Result result = mail.deliver(recipient, SUBJECT, body);
      if (result != ContactTransport.Result.SENT) {
        log.warn("検証メールを送れませんでした userId={} result={}", userId, result);
      }
    } catch (RuntimeException failure) {
      log.warn("検証メールの送信で例外が発生しました userId={} exception={}", userId, failure.getClass().getName());
    }
  }

  // 表示名は載せない。登録は匿名で誰の宛先でも名乗れるので、入力した文言が他人宛ての本文に混ざる。
  private String body(String token) {
    String link =
        appProperties.getScheme() + "://" + appProperties.getDomain() + CONFIRMATION_PATH + token;
    return "会員登録ありがとうございます。\n"
        + "次のリンクを開いて、メールアドレスの確認を完了してください。\n\n"
        + link
        + "\n\n"
        + "リンクの有効期限は 24 時間です。\n"
        + "このメールにお心当たりがない場合は、何もせずに破棄してください。\n";
  }

  private static String randomToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
