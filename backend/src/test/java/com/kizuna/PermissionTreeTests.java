package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.notificationdelivery.api.NotificationDeliveryController;
import com.kizuna.shared.security.Authorities;
import com.kizuna.shared.storescope.CurrentStore;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionTree;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 権限の二つの木（ADR 0042）を権限目録と全 handler に対して機械検証する。単店の端点（{@code @CurrentStore} を要する handler）は
 * 店舗の木の権限だけを名指し、グループの木の権限では開かない。
 */
class PermissionTreeTests {

  private static final Pattern PERMISSION_LITERAL =
      Pattern.compile("'" + Pattern.quote(Authorities.permission("")) + "([A-Z_]+)'");

  /** X-Store-ID の有無で店舗の行とグループ級の行を切り替える handler。単店とも店舗集合を跨ぐ端点とも言えず上の規則が成り立たないため、ここに列挙して個別に審査する。 */
  private static final Set<Class<?>> OPTIONAL_STORE_CONTROLLERS =
      Set.of(NotificationDeliveryController.class);

  @Test
  @DisplayName("どの権限もグループの木か店舗の木の一方にだけ属し、両方の木に権限があること")
  void everyPermissionBelongsToExactlyOneTree() {
    assertThat(PermissionCode.values())
        .as("属する木を持たない権限")
        .filteredOn(code -> code.getTree() == null)
        .isEmpty();
    assertThat(
            Arrays.stream(PermissionCode.values())
                .map(PermissionCode::getTree)
                .collect(Collectors.toSet()))
        .as("権限が属している木")
        .containsExactlyInAnyOrder(PermissionTree.values());
  }

  @Test
  @DisplayName("単店の端点はグループの木の権限を名指さないこと")
  void singleStoreHandlersNameOnlyStoreTreePermissions() throws Exception {
    List<String> offenders = new ArrayList<>();
    int singleStore = 0;
    for (ControllerHandlers.Handler handler : ControllerHandlers.all()) {
      Optional<CurrentStore> store = currentStore(handler.method());
      if (store.isEmpty() || !store.get().required()) continue;
      singleStore++;
      for (PermissionCode code : named(handler.method())) {
        if (code.getTree() != PermissionTree.STORE) offenders.add(handler.name() + ": " + code);
      }
    }

    assertThat(singleStore).as("@CurrentStore を要する handler の数").isGreaterThan(0);
    assertThat(offenders).as("グループの木の権限で開く単店の端点").isEmpty();
  }

  @Test
  @DisplayName("店舗を任意で受ける handler は個別に審査した一覧に限ること")
  void optionalStoreHandlersAreReviewed() throws Exception {
    Set<Class<?>> optional =
        ControllerHandlers.all().stream()
            .filter(
                handler -> currentStore(handler.method()).filter(s -> !s.required()).isPresent())
            .map(ControllerHandlers.Handler::type)
            .collect(Collectors.toSet());

    assertThat(optional)
        .as("@CurrentStore(required = false) を持つ Controller")
        .containsExactlyInAnyOrderElementsOf(OPTIONAL_STORE_CONTROLLERS);
  }

  private static Optional<CurrentStore> currentStore(Method method) {
    return Arrays.stream(method.getParameters())
        .map((Parameter parameter) -> parameter.getAnnotation(CurrentStore.class))
        .filter(annotation -> annotation != null)
        .findFirst();
  }

  private static List<PermissionCode> named(Method method) {
    PreAuthorize authorize = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
    if (authorize == null) return List.of();
    List<PermissionCode> codes = new ArrayList<>();
    Matcher matcher = PERMISSION_LITERAL.matcher(authorize.value());
    while (matcher.find()) codes.add(PermissionCode.valueOf(matcher.group(1)));
    return codes;
  }
}
