package com.kizuna.cast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastInvitation;
import com.kizuna.cast.domain.CastInvitationRepository;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 招待発行 API と一覧の招待状態を本物の PostgreSQL で検証する統合テスト（#327）。
 *
 * <p>発行は STORE_MANAGER 限定（{@code @PreAuthorize}）で、店舗授権と越権はインターセプタが担う。 クロス店舗分離は {@link
 * com.kizuna.menu.MenuCrossStoreIT} 流にリポジトリ直挿 + 実データ断言で固定する。
 */
class CastInvitationApiIT extends CrossStoreTestSupport {

  private static final String MANAGER_EMAIL = "tanaka.hanako@kizuna.test";
  private static final String HQ_ADMIN_EMAIL = "admin@kizuna.test";
  private static final String LINK_USER_EMAIL = "yamada.jiro@kizuna.test";
  private static final String PASSWORD = "pass";
  private static final String FOREIGN_DOMAIN = "cast-invitation-it.kizuna.test";

  @Autowired private CastRepository castRepository;
  @Autowired private CastInvitationRepository castInvitationRepository;
  @Autowired private StoreRepository storeRepository;
  @Autowired private PlatformUserRepository platformUserRepository;

  private String managerToken;
  private long foreignStoreId;

  @BeforeEach
  void prepareManagerAndForeignStore() {
    managerToken = platformToken(MANAGER_EMAIL, PASSWORD);
    Store foreign =
        storeRepository
            .findByDomain(FOREIGN_DOMAIN)
            .orElseGet(() -> storeRepository.save(new Store("招待IT他店舗", FOREIGN_DOMAIN, null)));
    foreignStoreId = foreign.getId();
  }

  @Test
  @DisplayName("店長は招待を発行でき、201 とトークン・約72h後の有効期限が返ること")
  void storeManagerCanIssueInvitation() {
    String castId = createCast(STORE_A, managerToken, "招待発行テスト");

    ResponseEntity<JsonNode> res = issueInvitation(castId, STORE_A, managerToken);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(res.getBody().path("token").asString()).isNotBlank();
    OffsetDateTime expiresAt = OffsetDateTime.parse(res.getBody().path("expires_at").asString());
    OffsetDateTime now = OffsetDateTime.now();
    assertThat(expiresAt).isAfter(now.plus(Duration.ofHours(71)));
    assertThat(expiresAt).isBefore(now.plus(Duration.ofHours(73)));
  }

  @Test
  @DisplayName("スタッフ（STORE_STAFF）は招待を発行できないこと（403）")
  void storeStaffCannotIssueInvitation() {
    String castId = createCast(STORE_A, managerToken, "スタッフ発行不可テスト");

    // token（基底クラスの yamada = STORE_STAFF）で発行 → @PreAuthorize で 403
    ResponseEntity<JsonNode> res = issueInvitation(castId, STORE_A, token);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("HQ_ADMIN が X-Role: store を名乗っても発行できないこと（インターセプタ 403）")
  void hqAdminCannotIssueViaStoreRole() {
    String castId = createCast(STORE_A, managerToken, "HQ発行不可テスト");
    String adminToken = platformToken(HQ_ADMIN_EMAIL, PASSWORD);

    ResponseEntity<String> res =
        rest.exchange(
            "/store/casts/" + castId + "/invitation",
            HttpMethod.POST,
            new HttpEntity<>(storeHeaders(STORE_A, adminToken)),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("授権外店舗の X-Store-ID での発行はインターセプタが fail-closed で 403 拒否すること")
  void unauthorizedStoreHeaderIsRejected() {
    // tanaka は店舗{1,2}のみ授権。foreignStoreId は授権外。
    ResponseEntity<String> res =
        rest.exchange(
            "/store/casts/dummy-cast/invitation",
            HttpMethod.POST,
            new HttpEntity<>(storeHeaders(foreignStoreId, managerToken)),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("平台身分と連携済みの档案には発行できないこと（400）")
  void issuingForLinkedCastIsRejected() {
    String castId = createCast(STORE_A, managerToken, "連携済み発行不可テスト");
    long linkUserId = platformUserRepository.findByEmail(LINK_USER_EMAIL).orElseThrow().getId();
    Cast cast = castRepository.findById(castId).orElseThrow();
    cast.linkPlatformUser(linkUserId);
    castRepository.save(cast);

    ResponseEntity<JsonNode> res = issueInvitation(castId, STORE_A, managerToken);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("再発行で旧 PENDING が INVALIDATED になり、新トークンのみが有効な PENDING であること")
  void reissueInvalidatesPreviousPending() {
    String castId = createCast(STORE_A, managerToken, "再発行テスト");

    String firstToken =
        issueInvitation(castId, STORE_A, managerToken).getBody().path("token").asString();
    String secondToken =
        issueInvitation(castId, STORE_A, managerToken).getBody().path("token").asString();

    assertThat(firstToken).isNotBlank().isNotEqualTo(secondToken);
    assertThat(castInvitationRepository.findByToken(firstToken).orElseThrow().getStatus())
        .isEqualTo(CastInvitation.Status.INVALIDATED);
    assertThat(castInvitationRepository.findByToken(secondToken).orElseThrow().getStatus())
        .isEqualTo(CastInvitation.Status.PENDING);
    assertThat(
            castInvitationRepository.findByCastIdAndStatus(castId, CastInvitation.Status.PENDING))
        .hasSize(1);
  }

  @Test
  @DisplayName("受諾確定済み(ACCEPTED)の招待がある档案で再発行しても、ACCEPTED 行が INVALIDATED に巻き戻らないこと")
  void reissueDoesNotRollBackAcceptedInvitation() {
    // 並行受諾が先に確定した状況を DB 状態で模す。再発行の失効は条件付き一括 UPDATE（WHERE status =
    // PENDING）で行うため、ACCEPTED の招待は失効の対象外となり INVALIDATED へ巻き戻らない（#327 codex 指摘）。
    // 档案は未紐づけのまま（受諾の紐づけ確定前の窓）なので、再発行自体は新 PENDING の発行として成功する。
    String castId = createCast(STORE_A, managerToken, "受諾済み巻き戻し防止テスト");
    CastInvitation accepted =
        CastInvitation.builder()
            .castId(castId)
            .token("cast-inv-it-accepted-" + System.nanoTime())
            .status(CastInvitation.Status.ACCEPTED)
            .expiresAt(OffsetDateTime.now().plusHours(72))
            .acceptedAt(OffsetDateTime.now())
            .build();
    accepted.setStoreId(STORE_A);
    String acceptedId = castInvitationRepository.save(accepted).getId();

    ResponseEntity<JsonNode> res = issueInvitation(castId, STORE_A, managerToken);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    // 受諾確定済みの招待は失効の対象外で、ACCEPTED のまま巻き戻っていない。
    assertThat(castInvitationRepository.findById(acceptedId).orElseThrow().getStatus())
        .isEqualTo(CastInvitation.Status.ACCEPTED);
    // 新たに発行された PENDING は 1 件のみ（ACCEPTED はそのまま残る）。
    assertThat(
            castInvitationRepository.findByCastIdAndStatus(castId, CastInvitation.Status.PENDING))
        .hasSize(1);
  }

  @Test
  @DisplayName("同一档案に PENDING 招待を2件保存すると部分ユニークインデックスが2件目を拒否すること（並行発行の直列化）")
  void secondPendingInvitationForSameCastIsRejected() {
    // 二重クリック等で issue() が並行した際に複数の有効トークンが発行される事態を、DB の
    // 部分ユニークインデックス uq_t_cast_invitations_pending_cast が塞ぐことを直接確認する。
    // テストスレッドは @StoreScoped を経由しないためリポジトリ直挿で PENDING を2件試みる。
    String castId = createCast(STORE_A, managerToken, "PENDING一意テスト");

    CastInvitation first = pendingInvitation(castId, "cast-inv-it-pending-a-" + System.nanoTime());
    castInvitationRepository.save(first);

    CastInvitation second = pendingInvitation(castId, "cast-inv-it-pending-b-" + System.nanoTime());
    assertThatThrownBy(() -> castInvitationRepository.save(second))
        .isInstanceOf(DataIntegrityViolationException.class);

    // 有効な PENDING は最初の 1 件のみ（2件目は永続化されていない）。
    assertThat(
            castInvitationRepository.findByCastIdAndStatus(castId, CastInvitation.Status.PENDING))
        .hasSize(1);
  }

  private CastInvitation pendingInvitation(String castId, String token) {
    CastInvitation invitation =
        CastInvitation.builder()
            .castId(castId)
            .token(token)
            .status(CastInvitation.Status.PENDING)
            .expiresAt(OffsetDateTime.now().plusHours(72))
            .build();
    invitation.setStoreId(STORE_A);
    return invitation;
  }

  @Test
  @DisplayName("他店舗の档案 ID を自店文脈から発行しても 400 で拒否され、他店舗のデータが不変であること")
  void crossStoreIssueIsRejectedAndForeignDataUnchanged() {
    // リポジトリ直挿（テストスレッドは @StoreScoped を経由せず storeFilter が無効なので他店舗にも書ける）。
    Cast foreignCast = Cast.builder().name("他店舗機密キャスト").build();
    foreignCast.setStoreId(foreignStoreId);
    String foreignCastId = castRepository.save(foreignCast).getId();

    // tanaka の授権店舗(店舗1)文脈から、他店舗の档案 ID を発行しようとする。
    ResponseEntity<JsonNode> res = issueInvitation(foreignCastId, STORE_A, managerToken);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    // 他店舗の档案には招待が一切作られていない（実データ断言）。
    assertThat(castInvitationRepository.findByCastIdIn(List.of(foreignCastId))).isEmpty();
    // 他店舗の档案の紐づけも変わっていない。
    assertThat(castRepository.findById(foreignCastId).orElseThrow().getPlatformUserId()).isNull();
  }

  @Test
  @DisplayName("一覧に招待状態の四態（未招待/招待中/期限切れ/連携済み）が正しく現れること")
  void listShowsFourInvitationStates() {
    String marker = "招待状態IT" + System.nanoTime();
    String notInvited = createCast(STORE_A, managerToken, marker + "未招待");
    String invited = createCast(STORE_A, managerToken, marker + "招待中");
    String expired = createCast(STORE_A, managerToken, marker + "期限切れ");
    String linked = createCast(STORE_A, managerToken, marker + "連携済み");

    // 招待中: API 発行で未期限 PENDING を作る。
    issueInvitation(invited, STORE_A, managerToken);

    // 期限切れ: 過去の有効期限を持つ PENDING を直挿する。
    CastInvitation expiredInvitation =
        CastInvitation.builder()
            .castId(expired)
            .token("cast-inv-it-expired-" + System.nanoTime())
            .status(CastInvitation.Status.PENDING)
            .expiresAt(OffsetDateTime.now().minusHours(1))
            .build();
    expiredInvitation.setStoreId(STORE_A);
    castInvitationRepository.save(expiredInvitation);

    // 連携済み: 档案に平台身分を紐づける。
    long linkUserId = platformUserRepository.findByEmail(LINK_USER_EMAIL).orElseThrow().getId();
    Cast linkedCast = castRepository.findById(linked).orElseThrow();
    linkedCast.linkPlatformUser(linkUserId);
    castRepository.save(linkedCast);

    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/casts?search=" + marker + "&size=500",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A, managerToken)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(statusOf(res.getBody(), notInvited)).isEqualTo("NOT_INVITED");
    assertThat(statusOf(res.getBody(), invited)).isEqualTo("INVITED");
    assertThat(statusOf(res.getBody(), expired)).isEqualTo("EXPIRED");
    assertThat(statusOf(res.getBody(), linked)).isEqualTo("LINKED");
  }

  private String statusOf(JsonNode page, String castId) {
    for (JsonNode node : page.path("content")) {
      if (castId.equals(node.path("id").asString())) {
        return node.path("invitation_status").asString();
      }
    }
    throw new AssertionError("一覧に档案 " + castId + " が見つかりません");
  }

  private String createCast(long storeId, String bearerToken, String name) {
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", storeHeaders(storeId, bearerToken)),
            JsonNode.class);
    assertThat(res.getStatusCode().is2xxSuccessful())
        .as("前提: store %d でのキャスト作成が成功すること", storeId)
        .isTrue();
    return res.getBody().path("id").asString();
  }

  private ResponseEntity<JsonNode> issueInvitation(
      String castId, long storeId, String bearerToken) {
    return rest.postForEntity(
        "/store/casts/" + castId + "/invitation",
        new HttpEntity<>(storeHeaders(storeId, bearerToken)),
        JsonNode.class);
  }

  private String platformToken(String email, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, password),
                headers),
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
}
