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

  /** 保存後に採番された第二テナントの実 id（定数 TENANT_B は使わない）。 */
  private long tenantBId;

  @BeforeEach
  void prepareSecondTenantWithMenu() {
    Tenant tenantB =
        tenantRepository
            .findByDomain(TENANT_B_DOMAIN)
            .orElseGet(
                () -> tenantRepository.save(new Tenant("統合テスト第二テナント", TENANT_B_DOMAIN, null)));
    tenantBId = tenantB.getId();

    if (!menuRepository.existsById(TENANT_B_MENU_ID)) {
      StoreMenu menu = new StoreMenu();
      menu.setId(TENANT_B_MENU_ID);
      menu.setStoreId(tenantBId);
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
  @DisplayName("tenant A の JWT に X-Tenant-ID: B を偽装しても 403 で拒否され、tenant B のデータも返らないこと")
  void spoofedTenantHeaderIsRejectedWithoutLeakingData() {
    // tenant A のシードユーザーで発行した JWT（tenantId claim = tenant A）に、
    // X-Tenant-ID ヘッダだけ tenant B を詐称したリクエスト。修正前は 200 で tenant B のメニューが漏れていた。
    ResponseEntity<String> res =
        rest.exchange(
            "/tenant/menus/me",
            HttpMethod.GET,
            new HttpEntity<>(tenantHeaders(tenantBId)),
            String.class);

    // JWT の tenantId claim と X-Tenant-ID ヘッダの不一致は TenantIdInterceptor が拒否する。
    // コントローラに到達しないため、tenant B のデータも本文も一切返らない。
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(res.getBody()).as("拒否時は本文を返さない（いずれのテナントのデータも含まない）").isNullOrEmpty();
  }

  @Test
  @DisplayName("tenant A のメニュー取得に tenant B のメニューが混入しないこと（正向対照）")
  void tenantAOnlySeesOwnSeedMenus() {
    List<String> names = rootMenuNames(TENANT_A);

    assertThat(names).contains(TENANT_A_SEED_ROOT_LABELS.toArray(String[]::new));
    assertThat(names).doesNotContain(TENANT_B_MENU_LABEL);
  }
}
