package com.kizuna.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.domain.Capability;
import com.kizuna.user.domain.CapabilityBundle;
import com.kizuna.user.domain.CapabilityBundleRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.Set;
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
 * 店舗存在性検証（{@link com.kizuna.store.infrastructure.StoreExistenceInterceptor}）の受け入れ IT（#429 / #398）。
 *
 * <p>陳旧化した JWT 授権集合や公開サイトの不正ヘッダで実在しない storeId が文脈に載った場合、500 や空 200 でなく 400 を返すことを固定する。 授権側は種子 HQ
 * admin に storeBridge が無いため、ALL_STORES + STORE コンソール能力（CAST_MANAGE）を持つ束を現場作成して用いる（{@link
 * com.kizuna.user.AuthorizationScenesIT} と同型）。
 */
class StoreExistenceInterceptorIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "pass";
  private static final String ALL_STORES_EMAIL = "store-existence-it-allstores@kizuna.test";

  /** 種子に無い束（DB データとして追加）。CAST_MANAGE は STORE コンソール能力なので storeBridge を導出させる。 */
  private static final String ALL_STORES_BUNDLE = "店舗存在性IT_全店舗キャスト管理";

  /** 実在しない storeId（自動採番の実 id 群と衝突しない大きな値）。 */
  private static final long NONEXISTENT_STORE_ID = 999_999_999L;

  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private CapabilityBundleRepository capabilityBundleRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private StoreRepository storeRepository;

  @BeforeEach
  void prepareFixture() {
    assertThat(storeRepository.existsById(NONEXISTENT_STORE_ID))
        .as("前提: 対象 storeId が実在しないこと")
        .isFalse();

    CapabilityBundle bundle =
        capabilityBundleRepository
            .findByName(ALL_STORES_BUNDLE)
            .orElseGet(
                () ->
                    capabilityBundleRepository.save(
                        CapabilityBundle.builder()
                            .name(ALL_STORES_BUNDLE)
                            .capabilities(Set.of(Capability.CAST_MANAGE))
                            .build()));
    platformUserRepository
        .findByEmail(ALL_STORES_EMAIL)
        .orElseGet(
            () ->
                platformUserRepository.save(
                    PlatformUser.builder()
                        .email(ALL_STORES_EMAIL)
                        .password(passwordEncoder.encode(PASSWORD))
                        .displayName("店舗存在性IT 全店舗")
                        .enabled(true)
                        .userType(UserType.STAFF)
                        .bundleIds(Set.of(bundle.getId()))
                        .storeScopeType(StoreScopeType.ALL_STORES)
                        .storeIds(Set.of())
                        .build()));
  }

  private String platformToken(String email) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, PASSWORD),
                headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 平台ログインが成功すること").isEqualTo(HttpStatus.OK);
    return res.getBody().path("token").asString();
  }

  @Test
  @DisplayName("ALL_STORES ユーザーが実在しない X-Store-ID を送ると 400（500 でも空 200 でもない）")
  void allStoresUserWithNonexistentStoreIdGets400() {
    String token = platformToken(ALL_STORES_EMAIL);
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.add("X-Role", "store");
    headers.add("X-Store-ID", String.valueOf(NONEXISTENT_STORE_ID));

    // /store/casts 一覧は PERM_CAST_MANAGE を要し、存在検証が無ければ storeFilter で空ページ 200 を返す端点。
    ResponseEntity<String> res =
        rest.exchange("/store/casts", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("匿名の公開端点でも実在しない X-Store-ID は 400（空 200 でない）")
  void anonymousPublicEndpointWithNonexistentStoreIdGets400() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Role", "store");
    headers.add("X-Store-ID", String.valueOf(NONEXISTENT_STORE_ID));

    // /store/casts/public は @PermitAll。存在検証が無ければ空リスト 200 を返す。
    ResponseEntity<String> res =
        rest.exchange(
            "/store/casts/public", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
