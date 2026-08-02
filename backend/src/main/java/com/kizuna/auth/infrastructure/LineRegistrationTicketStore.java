package com.kizuna.auth.infrastructure;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 未登録 LINE アカウントの登録チケット（Redis）。LINE で検証済みの LINE ユーザー ID を短命の不透明値の裏に保持し、 登録確定要求がその LINE ユーザー ID
 * を自称せずに済むようにする（自称できると任意の LINE アカウントを騙れる）。
 *
 * <p>チケットの消費は登録の成功時のみ: 参照（{@link #peek}）と削除（{@link #consume}）を分け、重複メールなどで登録が
 * 失敗した場合はチケットを残して入力の修正・再試行を許す（OAuth のやり直しを強いない）。成功後の再利用は削除が防ぎ、 削除前の並行二重登録は t_users の LINE ユーザー ID
 * 一意制約が最終防衛線として 409 に写像する。表示名は保持しない — 登録確定要求が利用者の編集後の表示名を送るため、検証時点の表示名は応答で前端へ返すだけでよい。
 */
@Component
@RequiredArgsConstructor
public class LineRegistrationTicketStore {

  private static final String KEY_PREFIX = "line:registration:";

  /** チケットの有効期間。登録確定画面でメールアドレスを入力するのに十分で、放置されたチケットは自然に消える。 */
  private static final Duration TTL = Duration.ofMinutes(10);

  private static final int TICKET_BYTES = 32;

  private final RedisTemplate<String, Object> redisTemplate;

  private final SecureRandom random = new SecureRandom();

  /** LINE ユーザー ID に対する新しいチケットを発行する。 */
  public String issue(String lineUserId) {
    byte[] bytes = new byte[TICKET_BYTES];
    random.nextBytes(bytes);
    String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    redisTemplate.opsForValue().set(KEY_PREFIX + ticket, lineUserId, TTL);
    return ticket;
  }

  /** チケット裏の LINE ユーザー ID を消費せずに返す。未知・期限切れ・使用済みなら空。 */
  public Optional<String> peek(String ticket) {
    if (ticket == null || ticket.isBlank()) {
      return Optional.empty();
    }
    Object lineUserId = redisTemplate.opsForValue().get(KEY_PREFIX + ticket);
    return Optional.ofNullable(lineUserId).map(Object::toString);
  }

  /** チケットを消費して LINE ユーザー ID を返す。未知・期限切れ・使用済みなら空。 */
  public Optional<String> consume(String ticket) {
    if (ticket == null || ticket.isBlank()) {
      return Optional.empty();
    }
    Object lineUserId = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + ticket);
    return Optional.ofNullable(lineUserId).map(Object::toString);
  }
}
