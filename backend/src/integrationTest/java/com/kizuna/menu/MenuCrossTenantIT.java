package com.kizuna.menu;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.kizuna.menu.domain.StoreMenu;
import com.kizuna.menu.domain.StoreMenuRepository;
import com.kizuna.shared.CrossTenantTestSupport;
import com.kizuna.tenant.domain.Tenant;
import com.kizuna.tenant.domain.TenantRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * StoreMenu のクロステナント分離を本物の PostgreSQL で検証する統合テスト（issue #227）。
 *
 * <p>StoreMenu には作成 API がなくデータは Liquibase シード（tenant 1 のみ）に由来するため、
 * リポジトリ直挿しで第二テナントとそのメニューを自前で用意する（リポジトリ直接呼び出しは {@code @TenantScoped} を経由せず tenantFilter
 * が無効なので、他テナントのデータも書ける）。 GET /tenant/menus/me が X-Tenant-ID の切替でテナント間のメニューを混在させないことを固定する。
 */
class MenuCrossTenantIT extends CrossTenantTestSupport {

  private static final String TENANT_B_DOMAIN = "menu-it.kizuna.test";
  private static final String TENANT_B_MENU_ID = "it-menu-b-root";
  private static final String TENANT_B_MENU_LABEL = "第二テナント専用メニュー";
  private static final List<String> TENANT_A_SEED_ROOT_LABELS = List.of("メイン", "業務管理", "HRM");

  @Autowired private TenantRepository tenantRepository;
  @Autowired private StoreMenuRepository menuRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** 保存後に採番された第二テナントの実 id（定数 TENANT_B は使わない）。 */
  private long tenantBId;

  @BeforeEach
  void prepareSecondTenantWithMenu() {
    // シードは central_tenants に明示 id=1 で投入しており IDENTITY シーケンスが
    // 進んでいない（issue #237）。save が id=1 と衝突しないよう先に補正する。
    jdbcTemplate.queryForObject(
        "select setval(pg_get_serial_sequence('central_tenants','id'),"
            + " (select max(id) from central_tenants))",
        Long.class);

    Tenant tenantB =
        tenantRepository
            .findByDomain(TENANT_B_DOMAIN)
            .orElseGet(
                () -> tenantRepository.save(new Tenant("統合テスト第二テナント", TENANT_B_DOMAIN, null)));
    tenantBId = tenantB.getId();

    if (!menuRepository.existsById(TENANT_B_MENU_ID)) {
      StoreMenu menu = new StoreMenu();
      menu.setId(TENANT_B_MENU_ID);
      menu.setTenantId(tenantBId);
      menu.setLabel(TENANT_B_MENU_LABEL);
      menu.setSortOrder(10);
      menuRepository.save(menu);
    }
  }

  private List<String> rootMenuNames(long tenantId) {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/tenant/menus/me",
            HttpMethod.GET,
            new HttpEntity<>(tenantHeaders(tenantId)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> names = new ArrayList<>();
    res.getBody().forEach(node -> names.add(node.path("name").asText()));
    return names;
  }

  @Test
  @DisplayName("tenant B のメニュー取得に tenant A のシードメニューが混入しないこと")
  void tenantBCannotSeeTenantAMenus() {
    List<String> names = rootMenuNames(tenantBId);

    // 漏洩がないことが本 issue の受入条件。containsExactly で「自分の分だけ」も同時に固定する
    assertThat(names).doesNotContainAnyElementsOf(TENANT_A_SEED_ROOT_LABELS);
    assertThat(names).containsExactly(TENANT_B_MENU_LABEL);
  }

  @Test
  @DisplayName("tenant A のメニュー取得に tenant B のメニューが混入しないこと（正向対照）")
  void tenantAOnlySeesOwnSeedMenus() {
    List<String> names = rootMenuNames(TENANT_A);

    assertThat(names).contains(TENANT_A_SEED_ROOT_LABELS.toArray(String[]::new));
    assertThat(names).doesNotContain(TENANT_B_MENU_LABEL);
  }
}
