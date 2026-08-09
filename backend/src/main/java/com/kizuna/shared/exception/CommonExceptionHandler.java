package com.kizuna.shared.exception;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.util.NamingStrategyImpls;

@Slf4j
@ControllerAdvice
public class CommonExceptionHandler {

  /** 例外の内部 message をワイヤ契約へ漏らさないよう、型ごとに固定文言へ写像する。 */
  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<Map<String, Object>> handle(AuthenticationException ex) {
    log.error(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", authErrorMessage(ex));
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
  }

  private String authErrorMessage(AuthenticationException ex) {
    if (ex instanceof DisabledException) {
      return "アカウントが無効化されています";
    }
    if (ex instanceof BadCredentialsException) {
      return "メールアドレスまたはパスワードが正しくありません";
    }
    return "認証に失敗しました";
  }

  /**
   * 検証違反を 400 へ映射する。{@code details} のキーは、同じフィールドを要求で送るときの綴り（snake_case）へ揃える。
   *
   * <p>束縛結果が持つのは Bean のプロパティパス（camelCase）である。Jackson の命名戦略は POJO のプロパティにしか 効かず {@code Map}
   * のキーには設計上効かないため、そのまま載せると同一フィールドが方向によって別名になる。
   *
   * <p>直しはこの組み立て時に閉じる。{@code Map} のキーを一律に変換する形（{@code addKeySerializer}）を採ると、 キー自体が利用者の定義したデータである
   * {@code CastResponse.customFields}／{@code StoreProfileResponse.customTexts} が静かに壊れる。
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handle(MethodArgumentNotValidException ex) {
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put(
        "error", Objects.requireNonNull(ex.getBindingResult().getFieldError()).getDefaultMessage());
    Map<String, String> fieldErrors = new HashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(fe -> fieldErrors.put(wireNameOf(fe.getField()), fe.getDefaultMessage()));
    body.put("details", fieldErrors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  /**
   * Bean のプロパティパスをワイヤ上の名前へ写す。
   *
   * <p>{@code spring.jackson.property-naming-strategy: SNAKE_CASE} が使うのと同一の実装を呼ぶので、綴りは Jackson
   * が実際に出す名前と一致する。前提は命名戦略が唯一の写像源であること — 個別に {@code @JsonProperty} で 別名を与えた項目があれば、その項目だけ一致しなくなる。
   *
   * <p>入れ子や添字を含むパス（{@code snsLinks[0].url}）も構造を保ったまま変換される。パスは丸ごと変換するので、
   * これが安全なのは束縛パスに利用者の定義したキーが現れないことが前提 — キー自体がデータである {@code Map} の
   * 要素へ制約を足すと、束縛パスの添字にそのキーが載り、書き換わってしまう。
   *
   * <p>他の {@code details} を返すハンドラはここを通さない — それらが持つのはクエリ／パス変数の宣言名や Jackson 適用後の名前で、いずれも既にワイヤ名だからである。
   */
  private static String wireNameOf(String propertyPath) {
    return NamingStrategyImpls.SNAKE_CASE.translate(propertyPath);
  }

  /**
   * 必須クエリ／リクエストパラメータの欠落を、既定の 500 でなくクライアント誤りとして 400 へ映射する。
   *
   * <p>{@code error} は利用者向けの固定文言、{@code details} は開発者向けにどのフィールドが原因かを示す。 フィールド名は API
   * 契約の一部なので露出してよいが、フレームワークの生 message は Java の型名や内部構造を含むため転送しない。
   */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<Map<String, Object>> handle(MissingServletRequestParameterException ex) {
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", "必須パラメータが不足しています");
    Map<String, String> details = new HashMap<>();
    details.put(ex.getParameterName(), "必須です");
    body.put("details", details);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  /** パスやクエリの値が宣言された型に変換できない（{@code /roles/abc} 等）ことを、クライアント誤りとして 400 へ映射する。 */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<Map<String, Object>> handle(MethodArgumentTypeMismatchException ex) {
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", "リクエストの形式が正しくありません");
    Map<String, String> details = new HashMap<>();
    details.put(ex.getName(), "値の形式が正しくありません");
    body.put("details", details);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  /**
   * 要求本文を解釈できない（壊れた JSON、列挙に無い値など）ことを、クライアント誤りとして 400 へ映射する。
   *
   * <p>原因が値の不一致であればワイヤ上のフィールド名を {@code details} に載せる。構文自体が壊れている場合は対象フィールドが 存在しないため {@code details}
   * を省く。
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, Object>> handle(HttpMessageNotReadableException ex) {
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", "リクエストの形式が正しくありません");
    String field = wireFieldOf(ex);
    if (field != null) {
      Map<String, String> details = new HashMap<>();
      details.put(field, "値の形式が正しくありません");
      body.put("details", details);
    }
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  /**
   * 解釈失敗の原因からワイヤ上のフィールド名を取り出す。取り出せなければ {@code null}。
   *
   * <p>{@link DatabindException#getPath()} は Jackson の命名戦略適用後の名前（本アプリでは snake_case）を返すため、 そのまま
   * {@code details} のキーに使える。配列添字の要素は名前を持たないので除く。
   */
  private static String wireFieldOf(HttpMessageNotReadableException ex) {
    if (!(ex.getCause() instanceof DatabindException cause)) {
      return null;
    }
    String path =
        cause.getPath().stream()
            .map(DatabindException.Reference::getPropertyName)
            .filter(Objects::nonNull)
            .collect(Collectors.joining("."));
    return path.isEmpty() ? null : path;
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> handle(AccessDeniedException ex) {
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", "アクセス権限がありません");
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
  }

  @ExceptionHandler(ServiceException.class)
  public ResponseEntity<Map<String, Object>> handle(ServiceException ex) {
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<Map<String, Object>> handle(ConflictException ex) {
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Map<String, Object>> handle(NotFoundException ex) {
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
  }

  /** 記録は error 級。到達すれば不変量が壊れており、通常のセッション期限切れに紛れて調査されないことを防ぐ。 */
  @ExceptionHandler(StaleSessionException.class)
  public ResponseEntity<Map<String, Object>> handle(StaleSessionException ex) {
    log.error(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", "セッションが無効です。再度ログインしてください");
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
  }

  @ExceptionHandler(ServiceUnavailableException.class)
  public ResponseEntity<Map<String, Object>> handle(ServiceUnavailableException ex) {
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", ex.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
  }

  /**
   * 一意制約違反<b>だけ</b>を 409 の兜底として受ける。検査してから書く経路が競合に敗れた場合の最後の一枚で、 業務上の重複判定そのものをここへ委ねてはならない。
   *
   * <p>他の整合性違反（FK・NOT NULL・CHECK）は利用者が是正できる誤りではなく実装の欠陥なので、 409 に化けさせず catch-all の 500
   * のまま大きく失敗させる。ここで一括して 409 にすると、 呼出側に「やり直せば直る」と誤って伝わり、欠陥が運用に埋もれる。
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, Object>> handle(DataIntegrityViolationException ex) {
    if (!isUniqueViolation(ex)) {
      return handle((Exception) ex);
    }
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", "既に登録されている値と重複しています");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }

  /** SQLSTATE 23505（unique_violation）で判定する。制約名の字面照合と違い、命名規約の変更に影響されない。 */
  private static boolean isUniqueViolation(DataIntegrityViolationException ex) {
    return ex.getMostSpecificCause() instanceof SQLException cause
        && "23505".equals(cause.getSQLState());
  }

  /** JPA @Version の楽観ロック競合（並行トランザクションの敗者）を 500 でなく 409 へ映射する。 */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<Map<String, Object>> handle(ObjectOptimisticLockingFailureException ex) {
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", "他の操作と競合しました。最新の状態を取得してやり直してください");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Map<String, Object>> handle(NoResourceFoundException ex) {
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handle(Exception ex) {
    log.error(ex.getMessage(), ex);
    Map<String, Object> body = new HashMap<>();
    body.put("error", "サーバー内部エラーが発生しました");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
