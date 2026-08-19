package com.kizuna.cast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.cast.application.CastInvitationAcceptanceService;
import com.kizuna.cast.domain.CastInvitation;
import com.kizuna.cast.domain.CastInvitationRepository;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

/**
 * 招待受諾 API（公開照会・新規登録受諾・既存アカウント受諾）を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>身分作成・档案紐づけ・招待状態遷移が単一トランザクションで確定すること、無効 token では副作用が出ないことを DB 断言で固定する。
 */
class PlatformCastInvitationAcceptanceIT extends CrossStoreTestSupport {

  private static final String MANAGER_EMAIL = "tanaka.hanako@kizuna.test";
  private static final String PASSWORD = "pass";
  private static final String EXISTING_CAST_EMAIL = "cast-existing-it@kizuna.test";

  @Autowired private CastRepository castRepository;
  @Autowired private CastInvitationRepository castInvitationRepository;
  @Autowired private StoreRepository storeRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private CastInvitationAcceptanceService acceptanceService;
  @Autowired private DataSource dataSource;

  private String managerToken;

  @BeforeEach
  void prepareManager() {
    managerToken = platformToken(MANAGER_EMAIL, PASSWORD);
  }

  @Test
  @DisplayName("新規登録受諾で CAST 身分が作成され、档案に紐づき、招待が ACCEPTED になること")
  void newUserAcceptanceLinksIdentityAndMarksAccepted() {
    String castId = createCast(STORE_A, "受諾新規テスト");
    String token = issue(castId, STORE_A);
    String email = "cast-new-it-" + System.nanoTime() + "@kizuna.test";

    ResponseEntity<JsonNode> res = acceptNewUser(token, email, "password1234", "新人花子");

    assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    PlatformUser user = platformUserRepository.findByEmail(email).orElseThrow();
    assertThat(user.getUserType()).isEqualTo(UserType.CAST);
    assertThat(user.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(user.getStoreIds()).contains(STORE_A);
    assertThat(castRepository.findById(castId).orElseThrow().getPlatformUserId())
        .isEqualTo(user.getId());
    assertThat(castInvitationRepository.findByToken(token).orElseThrow().getStatus())
        .isEqualTo(CastInvitation.Status.ACCEPTED);
  }

  @Test
  @DisplayName("期限切れ token の受諾は 4xx で拒否され、PlatformUser 数が不変であること")
  void expiredTokenAcceptanceIsRejectedWithoutSideEffects() {
    String castId = createCast(STORE_A, "期限切れ受諾テスト");
    String token =
        directInsertInvitation(
            castId, CastInvitation.Status.PENDING, OffsetDateTime.now().minusHours(1));
    String email = "cast-expired-it-" + System.nanoTime() + "@kizuna.test";
    long before = platformUserRepository.count();

    ResponseEntity<JsonNode> res = acceptNewUser(token, email, "password1234", "花子");

    assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    assertThat(platformUserRepository.count()).isEqualTo(before);
    assertThat(platformUserRepository.findByEmail(email)).isEmpty();
  }

  @Test
  @DisplayName("受諾済み token は再受諾できないこと（4xx）")
  void acceptedTokenCannotBeReaccepted() {
    String castId = createCast(STORE_A, "再受諾不可テスト");
    String token = issue(castId, STORE_A);
    acceptNewUser(
        token, "cast-first-it-" + System.nanoTime() + "@kizuna.test", "password1234", "花子");

    ResponseEntity<JsonNode> res =
        acceptNewUser(
            token, "cast-second-it-" + System.nanoTime() + "@kizuna.test", "password1234", "次郎");

    assertThat(res.getStatusCode().is4xxClientError()).isTrue();
  }

  @Test
  @DisplayName("再発行で失効した旧 token は受諾できないこと（4xx）")
  void invalidatedTokenIsRejected() {
    String castId = createCast(STORE_A, "失効token受諾不可テスト");
    String staleToken = issue(castId, STORE_A);
    issue(castId, STORE_A);

    ResponseEntity<JsonNode> res =
        acceptNewUser(
            staleToken,
            "cast-stale-it-" + System.nanoTime() + "@kizuna.test",
            "password1234",
            "花子");

    assertThat(res.getStatusCode().is4xxClientError()).isTrue();
  }

  @Test
  @DisplayName("既に登録済みのメールでの新規登録受諾は 4xx で拒否されること")
  void duplicateEmailRegistrationIsRejected() {
    String castId = createCast(STORE_A, "重複メール受諾テスト");
    String token = issue(castId, STORE_A);

    // 既存のシードユーザー（田中花子）の email で新規登録を試みる。
    ResponseEntity<JsonNode> res = acceptNewUser(token, MANAGER_EMAIL, "password1234", "花子");

    assertThat(res.getStatusCode().is4xxClientError()).isTrue();
  }

  @Test
  @DisplayName("短すぎるパスワードでの新規登録受諾は 400 で拒否され、副作用がないこと")
  void tooShortPasswordRegistrationIsRejected() {
    String castId = createCast(STORE_A, "短パスワード受諾テスト");
    String token = issue(castId, STORE_A);
    String email = "cast-shortpw-it-" + System.nanoTime() + "@kizuna.test";

    ResponseEntity<JsonNode> res = acceptNewUser(token, email, "a", "花子");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(platformUserRepository.findByEmail(email)).isEmpty();
  }

  @Test
  @DisplayName("列長(150)を超える表示名での新規登録受諾は 400 で拒否され、副作用がないこと")
  void tooLongDisplayNameRegistrationIsRejected() {
    String castId = createCast(STORE_A, "長すぎ表示名受諾テスト");
    String token = issue(castId, STORE_A);
    String email = "cast-longname-it-" + System.nanoTime() + "@kizuna.test";
    // platform_users.display_name は VARCHAR(150)。151 文字は列長超過で、@Size が無いと生の DB エラー(500)になる。
    String displayName = "あ".repeat(151);

    ResponseEntity<JsonNode> res = acceptNewUser(token, email, "password1234", displayName);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(platformUserRepository.findByEmail(email)).isEmpty();
  }

  @Test
  @DisplayName("列長(255)を超えるメールでの新規登録受諾は 400 で拒否され、副作用がないこと")
  void tooLongEmailRegistrationIsRejected() {
    String castId = createCast(STORE_A, "長すぎメール受諾テスト");
    String token = issue(castId, STORE_A);
    // t_users.email は VARCHAR(255)。@Email は形式のみで長さを見ないため、@Size が無いと列長超過の生 DB エラー(500)になる。
    // ローカル部は @Email の上限 64 文字、ドメインはラベル 63 文字以内で組み、形式は合法のまま 255 字を超える。
    String label = "a".repeat(63);
    String email = "b".repeat(64) + "@" + label + "." + label + "." + label + ".kizuna.test";
    long before = platformUserRepository.count();

    ResponseEntity<JsonNode> res = acceptNewUser(token, email, "password1234", "花子");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(platformUserRepository.count()).isEqualTo(before);
    assertThat(platformUserRepository.findByEmail(email)).isEmpty();
  }

  @Test
  @DisplayName("小文字化で列長(255)を超えるメールでの新規登録受諾は 400 で拒否され、副作用がないこと")
  void lowercaseExpandingEmailRegistrationIsRejected() {
    String castId = createCast(STORE_A, "伸長メール受諾テスト");
    String token = issue(castId, STORE_A);
    // U+0130 は永続化前の小文字化で 2 文字に伸長する。原文 146 字は @Email を通過するが
    // 小文字化後は 280 字となり VARCHAR(255) を超えるため、@Size は伸長を見込んだ上限で拒否する必要がある。
    String label = "İ".repeat(10);
    String email = "İ".repeat(64) + "@" + (label + ".").repeat(7) + "test";
    long before = platformUserRepository.count();

    ResponseEntity<JsonNode> res = acceptNewUser(token, email, "password1234", "花子");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(platformUserRepository.count()).isEqualTo(before);
  }

  @Test
  @DisplayName("既存 CAST アカウントの受諾で所属店舗が追加され、档案に紐づくこと")
  void existingCastAcceptanceAddsStoreAndLinks() {
    PlatformUser castUser = ensureExistingCastUser();
    String castBearer = platformToken(EXISTING_CAST_EMAIL, PASSWORD);
    // 田中は店舗{1,2}授権。既存 CAST は店舗{1}所属なので、店舗2の招待で店舗を追加する。
    String castId = createCast(STORE_B, "既存受諾店舗2テスト");
    String token = issue(castId, STORE_B);

    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/cast-invitations/acceptance/existing",
            HttpMethod.POST,
            new HttpEntity<>(tokenBody(token), bearer(castBearer)),
            JsonNode.class);

    assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    PlatformUser reloaded = platformUserRepository.findByEmail(EXISTING_CAST_EMAIL).orElseThrow();
    assertThat(reloaded.getStoreIds()).contains(STORE_A, STORE_B);
    assertThat(castRepository.findById(castId).orElseThrow().getPlatformUserId())
        .isEqualTo(castUser.getId());
    assertThat(castInvitationRepository.findByToken(token).orElseThrow().getStatus())
        .isEqualTo(CastInvitation.Status.ACCEPTED);
  }

  @Test
  @DisplayName("同一 SPECIFIC_STORES CAST の並行受諾で店舗集合の更新が取りこぼされないこと")
  void concurrentExistingAcceptancesDoNotLoseStoreUpdates() throws Exception {
    String email = "cast-concurrent-it-" + System.nanoTime() + "@kizuna.test";
    // 店舗{1} 所属の CAST を用意する。
    platformUserRepository.save(
        PlatformUser.builder()
            .email(email)
            .password(passwordEncoder.encode(PASSWORD))
            .displayName("並行受諾IT")
            .enabled(true)
            .userType(UserType.CAST)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(STORE_A))
            .build());

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      // ロックが無いと storeIds の read-modify-write が競合し、後着の save が先着の追加を上書きする。
      // 片方が既存店舗{1}(冪等)、もう片方が新規店舗{2} を追加する構図を反復し、
      // 「最後書き勝ちで店舗2が消える」取りこぼしを確実に顕在化させる。
      for (int i = 0; i < 8; i++) {
        // 反復ごとに新しい档案と招待を用意する（招待は使い捨て、档案は二重紐づけ防御があるため）。
        String castA = createCast(STORE_A, "並行受諾A" + i);
        String castB = createCast(STORE_B, "並行受諾B" + i);
        String tokenA =
            directInsertInvitation(
                castA, STORE_A, CastInvitation.Status.PENDING, OffsetDateTime.now().plusHours(1));
        String tokenB =
            directInsertInvitation(
                castB, STORE_B, CastInvitation.Status.PENDING, OffsetDateTime.now().plusHours(1));
        // 前反復の追加をリセットし、店舗{1} からやり直す。
        resetStores(email, Set.of(STORE_A));

        CyclicBarrier barrier = new CyclicBarrier(2);
        Future<?> f1 = pool.submit(acceptTask(barrier, tokenA, email));
        Future<?> f2 = pool.submit(acceptTask(barrier, tokenB, email));
        f1.get(30, TimeUnit.SECONDS);
        f2.get(30, TimeUnit.SECONDS);

        PlatformUser reloaded = platformUserRepository.findByEmail(email).orElseThrow();
        assertThat(reloaded.getStoreIds())
            .as("反復 %d: 並行受諾後に両店舗が保持されること", i)
            .contains(STORE_A, STORE_B);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName("CAST 以外のアカウントでの既存受諾は 403 で拒否されること")
  void existingAcceptanceWithNonCastRoleIsForbidden() {
    String castId = createCast(STORE_A, "既存受諾非CASTテスト");
    String token = issue(castId, STORE_A);

    // token（基底クラスの yamada = STAFF（店舗スタッフ束））で既存受諾 → @PreAuthorize ROLE_CAST で 403
    ResponseEntity<String> res =
        rest.exchange(
            "/platform/cast-invitations/acceptance/existing",
            HttpMethod.POST,
            new HttpEntity<>(tokenBody(token), bearer(this.token)),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("未認証の既存受諾は 401/403 で拒否されること")
  void existingAcceptanceUnauthenticatedIsRejected() {
    String castId = createCast(STORE_A, "既存受諾未認証テスト");
    String token = issue(castId, STORE_A);

    ResponseEntity<String> res =
        rest.exchange(
            "/platform/cast-invitations/acceptance/existing",
            HttpMethod.POST,
            new HttpEntity<>(tokenBody(token), jsonHeaders()),
            String.class);

    assertThat(res.getStatusCode().value()).isIn(401, 403);
  }

  @Test
  @DisplayName(
      "陳腐な Bearer を付けたまま招待照会・新規登録受諾を叩いても匿名で通ること"
          + "（招待 inline flow の呼び出し元は既存 token cookie を持ちうるため、坏 token に塞がれてはならない）")
  void viewAndAcceptWithBrokenBearerStillSucceedAnonymously() {
    String castId = createCast(STORE_A, "陳腐Bearer受諾テスト");
    String token = issue(castId, STORE_A);
    String email = "cast-broken-bearer-it-" + System.nanoTime() + "@kizuna.test";

    ResponseEntity<JsonNode> view =
        rest.postForEntity(
            "/platform/cast-invitations/view",
            new HttpEntity<>(tokenBody(token), jsonHeadersWithBrokenBearer()),
            JsonNode.class);
    assertThat(view.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(view.getBody().path("status").asString()).isEqualTo("VALID");

    String body =
        String.format(
            "{\"token\": \"%s\", \"email\": \"%s\", \"password\": \"password1234\","
                + " \"display_name\": \"陳腐Bearer花子\"}",
            token, email);
    ResponseEntity<JsonNode> accept =
        rest.postForEntity(
            "/platform/cast-invitations/acceptance",
            new HttpEntity<>(body, jsonHeadersWithBrokenBearer()),
            JsonNode.class);

    assertThat(accept.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(platformUserRepository.findByEmail(email)).isPresent();
  }

  @Test
  @DisplayName("照会は VALID/EXPIRED/USED の 3 状態と店舗名・档案名を返すこと")
  void viewReturnsValidExpiredUsedStates() {
    // VALID
    String validCast = createCast(STORE_A, "照会VALIDテスト");
    String validToken = issue(validCast, STORE_A);
    ResponseEntity<JsonNode> valid = viewInvitation(validToken);
    assertThat(valid.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(valid.getBody().path("status").asString()).isEqualTo("VALID");
    assertThat(valid.getBody().path("cast_name").asString()).isEqualTo("照会VALIDテスト");
    assertThat(valid.getBody().path("store_name").asString())
        .isEqualTo(storeRepository.findById(STORE_A).orElseThrow().getName());

    // EXPIRED
    String expiredCast = createCast(STORE_A, "照会EXPIREDテスト");
    String expiredToken =
        directInsertInvitation(
            expiredCast, CastInvitation.Status.PENDING, OffsetDateTime.now().minusHours(1));
    assertThat(viewInvitation(expiredToken).getBody().path("status").asString())
        .isEqualTo("EXPIRED");

    // USED
    String usedCast = createCast(STORE_A, "照会USEDテスト");
    String usedToken =
        directInsertInvitation(
            usedCast, CastInvitation.Status.INVALIDATED, OffsetDateTime.now().plusHours(1));
    assertThat(viewInvitation(usedToken).getBody().path("status").asString()).isEqualTo("USED");
  }

  @Test
  @DisplayName("新規登録受諾が招待行より先にキャスト行を押さえること")
  void newUserAcceptanceTakesTheCastRowBeforeTheInvitationRow() throws Exception {
    String castId = createCast(STORE_A, "受諾ロック順新規テスト");
    String token = issue(castId, STORE_A);
    String email = "cast-lockorder-new-it-" + System.nanoTime() + "@kizuna.test";

    assertCastRowIsHeldWhileWaitingOnTheInvitation(
        castId, token, () -> acceptNewUser(token, email, "password1234", "順序花子"));
  }

  @Test
  @DisplayName("既存アカウント受諾が招待行より先にキャスト行を押さえること")
  void existingAcceptanceTakesTheCastRowBeforeTheInvitationRow() throws Exception {
    ensureExistingCastUser();
    String castBearer = platformToken(EXISTING_CAST_EMAIL, PASSWORD);
    String castId = createCast(STORE_B, "受諾ロック順既存テスト");
    String token = issue(castId, STORE_B);

    assertCastRowIsHeldWhileWaitingOnTheInvitation(
        castId,
        token,
        () ->
            rest.exchange(
                "/platform/cast-invitations/acceptance/existing",
                HttpMethod.POST,
                new HttpEntity<>(tokenBody(token), bearer(castBearer)),
                JsonNode.class));
  }

  @Test
  @DisplayName("受諾が档案より先に店舗行を押さえること")
  void acceptanceTakesTheStoreRowBeforeTheCastRow() throws Exception {
    // 受諾は身分の所属店舗（t_user_stores）を書くので、その INSERT が店舗行に key share を要求する。
    // 档案を先に押さえると、店舗削除（店舗行を押さえてから配下へ CASCADE）と逆順になり環になる（ADR 0016）。
    String castId = createCast(STORE_A, "受諾ロック順店舗テスト");
    String token = issue(castId, STORE_A);
    String email = "cast-lockorder-store-it-" + System.nanoTime() + "@kizuna.test";

    ExecutorService pool = Executors.newSingleThreadExecutor();
    try (Connection holder = dataSource.getConnection()) {
      holder.setAutoCommit(false);
      try (var statement =
          holder.prepareStatement("SELECT id FROM t_stores WHERE id = ? FOR UPDATE")) {
        statement.setLong(1, STORE_A);
        assertThat(statement.executeQuery().next()).as("前提: 店舗行を押さえられること").isTrue();
      }

      Future<ResponseEntity<JsonNode>> waiting =
          pool.submit(() -> acceptNewUser(token, email, "password1234", "店舗順序花子"));
      assertThatThrownBy(() -> waiting.get(3, TimeUnit.SECONDS))
          .as("前提: 店舗行が押さえられている間は進めないこと")
          .isInstanceOf(TimeoutException.class);

      try (var statement =
          holder.prepareStatement("SELECT id FROM t_casts WHERE id = ? FOR UPDATE NOWAIT")) {
        statement.setString(1, castId);
        assertThat(statement.executeQuery().next()).as("店舗で待っている間、档案行はまだ押さえられていないこと").isTrue();
      }

      holder.rollback();
      assertThat(waiting.get(30, TimeUnit.SECONDS).getStatusCode().is2xxSuccessful()).isTrue();
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName("再発行も旧票を失効させる前に档案行を押さえること")
  void reissueTakesTheCastRowBeforeInvalidatingTheOldInvitation() throws Exception {
    // 再発行は旧 PENDING を失効させてから新しい招待を建てる。その INSERT が档案行に key share を
    // 要求するので、失効を先に済ませると「招待 → 档案」になり、受諾（档案 → 招待）と環になる。
    String castId = createCast(STORE_A, "再発行ロック順テスト");
    String staleToken = issue(castId, STORE_A);

    assertCastRowIsHeldWhileWaitingOnTheInvitation(
        castId,
        staleToken,
        () ->
            rest.postForEntity(
                "/store/casts/" + castId + "/invitation",
                new HttpEntity<>(storeHeaders(STORE_A, managerToken)),
                JsonNode.class));
  }

  /**
   * 受諾が「キャスト行 → 招待行」の順に押さえることを、順序そのもので見る（ADR 0016）。
   *
   * <p>「待つかどうか」では分かれない。逆順の実装でも招待行で待つからである。招待行を外から押さえて 受諾を待たせ、その間にキャスト行を待たずに取れるか試す —
   * 取れてしまえば受諾はまだキャストへ 手を伸ばしていない。
   */
  private void assertCastRowIsHeldWhileWaitingOnTheInvitation(
      String castId, String token, Callable<ResponseEntity<JsonNode>> accept) throws Exception {
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try (Connection holder = dataSource.getConnection()) {
      holder.setAutoCommit(false);
      try (var statement =
          holder.prepareStatement("SELECT id FROM t_cast_invitations WHERE token = ? FOR UPDATE")) {
        statement.setString(1, token);
        assertThat(statement.executeQuery().next()).as("前提: 招待行を押さえられること").isTrue();
      }

      Future<ResponseEntity<JsonNode>> waiting = pool.submit(accept);
      assertThatThrownBy(() -> waiting.get(3, TimeUnit.SECONDS))
          .as("前提: 招待行が押さえられている間は進めないこと")
          .isInstanceOf(TimeoutException.class);

      try (var statement =
          holder.prepareStatement("SELECT id FROM t_casts WHERE id = ? FOR UPDATE NOWAIT")) {
        statement.setString(1, castId);
        assertThatThrownBy(statement::executeQuery)
            .as("招待で待っている間、キャスト行は既に押さえられていること")
            .isInstanceOfSatisfying(
                SQLException.class,
                // 55P03 = lock_not_available。型だけ見ると表名の打ち間違いでも緑になる。
                e -> assertThat(e.getSQLState()).isEqualTo("55P03"));
      }

      holder.rollback();
      assertThat(waiting.get(30, TimeUnit.SECONDS).getStatusCode().is2xxSuccessful()).isTrue();
    } finally {
      pool.shutdownNow();
    }
  }

  private PlatformUser ensureExistingCastUser() {
    return platformUserRepository
        .findByEmail(EXISTING_CAST_EMAIL)
        .orElseGet(
            () ->
                platformUserRepository.save(
                    PlatformUser.builder()
                        .email(EXISTING_CAST_EMAIL)
                        .password(passwordEncoder.encode(PASSWORD))
                        .displayName("既存キャストIT")
                        .enabled(true)
                        .userType(UserType.CAST)
                        .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                        .storeIds(Set.of(STORE_A))
                        .build()));
  }

  private String createCast(long storeId, String name) {
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", storeHeaders(storeId, managerToken)),
            JsonNode.class);
    assertThat(res.getStatusCode().is2xxSuccessful())
        .as("前提: store %d でのキャスト作成が成功すること", storeId)
        .isTrue();
    return res.getBody().path("id").asString();
  }

  private String issue(String castId, long storeId) {
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/store/casts/" + castId + "/invitation",
            new HttpEntity<>(storeHeaders(storeId, managerToken)),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 招待発行が成功すること").isEqualTo(HttpStatus.CREATED);
    return res.getBody().path("token").asString();
  }

  private String directInsertInvitation(
      String castId, CastInvitation.Status status, OffsetDateTime expiresAt) {
    return directInsertInvitation(castId, STORE_A, status, expiresAt);
  }

  private String directInsertInvitation(
      String castId, long storeId, CastInvitation.Status status, OffsetDateTime expiresAt) {
    String token = "cast-inv-it-" + System.nanoTime();
    CastInvitation invitation =
        CastInvitation.builder()
            .castId(castId)
            .token(token)
            .status(status)
            .expiresAt(expiresAt)
            .build();
    invitation.setStoreId(storeId);
    castInvitationRepository.save(invitation);
    return token;
  }

  private void resetStores(String email, Set<Long> stores) {
    PlatformUser user = platformUserRepository.findByEmail(email).orElseThrow();
    user.reassignStores(StoreScopeType.SPECIFIC_STORES, stores);
    platformUserRepository.save(user);
  }

  private Runnable acceptTask(CyclicBarrier barrier, String token, String email) {
    return () -> {
      try {
        barrier.await(10, TimeUnit.SECONDS);
        acceptanceService.acceptAsExistingUser(token, email);
      } catch (Exception ignored) {
        // 競合下では例外（デッドロック等）もあり得るが、取りこぼしは最終状態の断言で検証する。
      }
    };
  }

  private ResponseEntity<JsonNode> acceptNewUser(
      String token, String email, String password, String displayName) {
    String body =
        String.format(
            "{\"token\": \"%s\", \"email\": \"%s\", \"password\": \"%s\", \"display_name\": \"%s\"}",
            token, email, password, displayName);
    return rest.postForEntity(
        "/platform/cast-invitations/acceptance",
        new HttpEntity<>(body, jsonHeaders()),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> viewInvitation(String token) {
    return rest.postForEntity(
        "/platform/cast-invitations/view",
        new HttpEntity<>(tokenBody(token), jsonHeaders()),
        JsonNode.class);
  }

  /** トークンはパスではなく本文で運ぶ（パスはリクエストターゲットとしてアクセスログに残るため）。 */
  private static String tokenBody(String token) {
    return String.format("{\"token\": \"%s\"}", token);
  }

  private String platformToken(String email, String password) {
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, password),
                jsonHeaders()),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 平台ログインが成功すること").isEqualTo(HttpStatus.OK);
    String t = res.getBody().path("token").asString();
    assertThat(t).isNotBlank();
    return t;
  }

  private HttpHeaders storeHeaders(long storeId, String bearerToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(storeId));
    headers.setBearerAuth(bearerToken);
    return headers;
  }

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  /** 前端が付けうる陳腐な token cookie を模した、解読不能な Authorization ヘッダ。 */
  private static HttpHeaders jsonHeadersWithBrokenBearer() {
    HttpHeaders headers = jsonHeaders();
    headers.setBearerAuth("stale-or-broken-token-value");
    return headers;
  }

  private static HttpHeaders bearer(String bearerToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(bearerToken);
    return headers;
  }
}
