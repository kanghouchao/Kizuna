package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.persistence.StoreScopedEntity;
import com.kizuna.shared.storescope.StoreFilterEnable;
import com.kizuna.shared.storescope.StoreScopeExempt;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.storescope.StoreSetFilterEnable;
import com.kizuna.shared.storescope.StoreSetScoped;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 店舗スコープ表を読む application 層の方法が、作用域を<b>宣言</b>していることを機械検証する。
 *
 * <p>{@code StoreScopedEntity} を型引数に持つ Spring Data repository を注入したクラスの public
 * 方法は、{@code @StoreScoped} / {@code @StoreSetScoped} / {@code @StoreScopeExempt}
 * のいずれか一つを必ず持つ。無宣言は「店舗を跨いで読める読み口」を静默に生む。豁免リストは持たない — 意図的な素通しは注釈と理由で名指しさせる。
 *
 * <p>走査の前提と限界（自己申告）:
 *
 * <ul>
 *   <li>注入は<b>宣言フィールドと構築子引数の型</b>で判定する。方法本体は反射から見えないので、判定はクラス粒度＝過剰近似 — repository
 *       を触らない方法も宣言を要求される（その場合は「委譲するだけ」を {@code reason} に書く）。
 *   <li>方法局所での repository 取得（{@code getBean} 等）、api 層のクラス、Spring bean でないクラスは見えない。
 *   <li>検査対象はクラス自身が宣言した非 private・非 static の方法（package-private も他 bean からの入口になるため含む）。 継承した方法は見ない。入口が
 *       private 補助方法へ委譲する形は、入口が検査されるので覆える。
 *   <li>これは<b>宣言</b>の監査であって効果の監査ではない。{@code StoreFilterEnable} は fail-open
 *       なので、宣言済みでも店舗文脈が無ければ濾過されない。
 * </ul>
 *
 * <p>{@code StoreScopeExecutor} は作用域の宣言として数えない。あれは {@code StoreContext} を確立するだけで Hibernate
 * フィルタは有効化せず（有効化は aspect のみが行う）、加えて方法本体を見ない反射では利用の静的検出自体が信頼できない。
 */
class StoreScopeDeclarationTests {

  @Test
  @DisplayName("店舗スコープ repository を注入した application クラスの全方法（非 private）が作用域を宣言していること")
  void allStoreScopedApplicationMethodsDeclareScope() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

    List<String> undeclared = new ArrayList<>();
    List<String> multiplyDeclared = new ArrayList<>();
    List<String> blankReasons = new ArrayList<>();
    List<String> scanned = new ArrayList<>();
    Set<String> repositories = new LinkedHashSet<>();
    int methods = 0;
    for (var candidate : scanner.findCandidateComponents("com.kizuna")) {
      Class<?> type = Class.forName(candidate.getBeanClassName());
      if (!isApplicationLayer(type)) {
        continue;
      }
      Set<String> injected = injectedStoreScopedRepositories(type);
      if (injected.isEmpty()) {
        continue;
      }
      scanned.add(type.getSimpleName());
      repositories.addAll(injected);
      for (Method method : type.getDeclaredMethods()) {
        if (!isAudited(method)) {
          continue;
        }
        methods++;
        String name = type.getName() + "#" + method.getName();
        int declarations = countScopeDeclarations(method);
        if (declarations == 0) {
          undeclared.add(name);
        } else if (declarations > 1) {
          multiplyDeclared.add(name);
        }
        StoreScopeExempt exempt =
            AnnotatedElementUtils.findMergedAnnotation(method, StoreScopeExempt.class);
        if (exempt != null && exempt.reason().isBlank()) {
          blankReasons.add(name);
        }
      }
    }

    // 暗黙の no-op 防止: 走査が実際にクラス・方法・店舗スコープ repository を捉えていることを担保する。
    assertThat(scanned).as("店舗スコープ repository を注入した application クラス").isNotEmpty();
    assertThat(methods).as("検査した方法の総数").isGreaterThan(0);
    assertThat(repositories).as("型引数が StoreScopedEntity を継承する repository").isNotEmpty();

    assertThat(undeclared)
        .as("@StoreScoped / @StoreSetScoped / @StoreScopeExempt のいずれも宣言していない方法（店舗フィルタ無しで読める）")
        .isEmpty();
    assertThat(multiplyDeclared).as("作用域宣言を二つ以上持つ方法（どれが効くか読めない）").isEmpty();
    assertThat(blankReasons).as("reason が空白の @StoreScopeExempt（理由の無い豁免は宣言にならない）").isEmpty();
  }

  /**
   * 店舗フィルタの aspect が、トランザクション advisor より<b>内側</b>で回ることを固定する。
   *
   * <p>外側へ出ると、OSIV=off の共有 EntityManager は {@code unwrap} のたびに使い捨ての Session を作って即閉じるため、フィルタは死んだ
   * Session に掛かり本体は別 Session で走る。proxy 鎖は {@code @Order} の小さい方が外側なので、不変量は「aspect の order ≧ tx
   * advisor の order」。
   *
   * <p>同値のときの前後は advisor の収集順（素の Advisor bean が AspectJ 由来より先）で決まり、{@code @Order} では固定されない。tx
   * advisor は最低優先度に居るため、aspect 側でそれ以上の値は取れない。
   */
  @Test
  @DisplayName("店舗フィルタの aspect がトランザクション advisor より内側の @Order を宣言していること")
  void filterAspectsRunInsideTransactionAdvisor() {
    EnableTransactionManagement enableTx =
        AnnotatedElementUtils.findMergedAnnotation(
            Application.class, EnableTransactionManagement.class);
    assertThat(enableTx).as("@EnableTransactionManagement の宣言").isNotNull();
    int transactionAdvisorOrder = enableTx.order();

    assertThat(OrderUtils.getOrder(StoreFilterEnable.class))
        .as("StoreFilterEnable の @Order（tx advisor の %d より小さいと外側へ出る）", transactionAdvisorOrder)
        .isNotNull()
        .isGreaterThanOrEqualTo(transactionAdvisorOrder);
    assertThat(OrderUtils.getOrder(StoreSetFilterEnable.class))
        .as("StoreSetFilterEnable の @Order（tx advisor の %d より小さいと外側へ出る）", transactionAdvisorOrder)
        .isNotNull()
        .isGreaterThanOrEqualTo(transactionAdvisorOrder);
  }

  /** {@code com.kizuna.<module>.application} 本体とその下位パッケージを application 層とみなす。 */
  private static boolean isApplicationLayer(Class<?> type) {
    String pkg = type.getPackageName();
    return pkg.endsWith(".application") || pkg.contains(".application.");
  }

  /** 宣言フィールドと構築子引数の型から、店舗スコープ repository の注入を拾う。 */
  private static Set<String> injectedStoreScopedRepositories(Class<?> type) {
    Set<String> found = new LinkedHashSet<>();
    for (Field field : type.getDeclaredFields()) {
      if (isStoreScopedRepository(field.getType())) {
        found.add(field.getType().getSimpleName());
      }
    }
    for (Constructor<?> constructor : type.getDeclaredConstructors()) {
      for (Class<?> parameter : constructor.getParameterTypes()) {
        if (isStoreScopedRepository(parameter)) {
          found.add(parameter.getSimpleName());
        }
      }
    }
    return found;
  }

  /** Spring Data repository の実体型引数が {@link StoreScopedEntity} を継承するか。 */
  private static boolean isStoreScopedRepository(Class<?> type) {
    if (!Repository.class.isAssignableFrom(type)) {
      return false;
    }
    Class<?> entity = ResolvableType.forClass(type).as(Repository.class).resolveGeneric(0);
    return entity != null && StoreScopedEntity.class.isAssignableFrom(entity);
  }

  /** 検査対象は「クラス自身が宣言した非 private・非 static の方法」。合成・bridge と Object の override は外す。 */
  private static boolean isAudited(Method method) {
    if (method.isSynthetic() || method.isBridge()) {
      return false;
    }
    int modifiers = method.getModifiers();
    if (Modifier.isPrivate(modifiers) || Modifier.isStatic(modifiers)) {
      return false;
    }
    try {
      Object.class.getMethod(method.getName(), method.getParameterTypes());
      return false;
    } catch (NoSuchMethodException expected) {
      return true;
    }
  }

  private static int countScopeDeclarations(Method method) {
    int count = 0;
    if (AnnotatedElementUtils.hasAnnotation(method, StoreScoped.class)) {
      count++;
    }
    if (AnnotatedElementUtils.hasAnnotation(method, StoreSetScoped.class)) {
      count++;
    }
    if (AnnotatedElementUtils.hasAnnotation(method, StoreScopeExempt.class)) {
      count++;
    }
    return count;
  }
}
