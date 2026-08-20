package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderFeeLine;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 「内訳の和＝合計」を集約の外から破れないことを機械検証する。
 *
 * <p>この不変量は跨表のため宣言的な DB 制約では書けず、trigger も導入しない。成立の根拠は「合計を書けるのは行を動かす集約の行為メソッドだけ」という
 * ひとつの事実だけで、書き口が増えた瞬間に静默で崩れる — 崩れても読み書きは通り、一覧の合計と内訳が食い違うことだけが残る。 その欠陥類をここで閉じる。
 *
 * <p>DB 側の符号 CHECK（{@code ck_t_order_fee_lines_amount_sign}）は行の符号までしか見ない。行の総和と合計の一致は見られないため、
 * 迂回の検出はこの適応度テストと統合テストが受け持つ。
 */
class OrderFeeLineAggregationTests {

  private static final Path MAIN_SOURCES = Paths.get("src/main/java");

  /** 集約の外から合計・明細を組み立てる形（builder 経由の書き込み）。読み出しは含まない。 */
  private static final Pattern AGGREGATE_BYPASS = Pattern.compile("\\.totalFee\\(|\\.feeLines\\(");

  /**
   * 受注の集約が持つ表・列・実体属性。一括更新から名指されれば迂回である。
   *
   * <p>素の SQL の字面（表名・列名）だけでなく JPQL の字面（属性名・実体名）も見る。同じ迂回が 2 つの書き方で
   * 書けるのに片方しか見ないと、検査の通過が「迂回が無いこと」を意味しなくなる。
   *
   * <p>受注を名指す一括更新そのものは正当なものがある（顧客統合の付け替えが {@code o.customerId} を書く）ため、 実体側は<b>書き込み先の属性</b>で見分ける —
   * 名指す実体ではなく、何を代入するかが迂回か否かを決める。
   */
  private static final List<String> ORDER_WRITE_TARGETS =
      List.of("t_order_fee_lines", "total_fee", "t_orders", "totalFee", "OrderFeeLine");

  @Test
  @DisplayName("合計と明細に公開の書き込み口が無いこと")
  void aggregateExposesNoMutatorForTheDerivedTotalOrItsLines() {
    // 基底の StoreScopedEntity は類レベルの @Setter を持つため、フィールドを消すだけでは口が閉じたと言えない
    assertThat(Order.class.getMethods())
        .extracting(Method::getName)
        .doesNotContain("setTotalFee", "setFeeLines");
    assertThat(OrderFeeLine.class.getMethods())
        .extracting(Method::getName)
        .doesNotContain("setKind", "setName", "setAmount");
  }

  @Test
  @DisplayName("集約の外から合計・明細を組み立てる書き方が本体に無いこと")
  void noProductionSourceOutsideTheAggregateBuildsTheTotalOrItsLines() throws IOException {
    List<String> offenders =
        javaSources()
            .filter(path -> !path.endsWith("Order.java"))
            .filter(path -> AGGREGATE_BYPASS.matcher(read(path)).find())
            .map(Path::toString)
            .toList();

    assertThat(offenders).as("合計は行を動かす集約の行為メソッドだけが書ける。ここに現れた経路は和≠合計を作れる").isEmpty();
  }

  @Test
  @DisplayName("受注の表を名指す一括更新・native 更新が無いこと")
  void noBulkOrNativeWriteTargetsTheOrderAggregate() throws IOException {
    List<String> offenders =
        javaSources()
            .filter(
                path -> {
                  String source = read(path);
                  // 註記は「@」を付けない字面で見る。完全修飾で書かれた形（規約違反だが書ける）を
                  // 取りこぼすと、検査の空振りが迂回の許可に見えてしまう。
                  return (source.contains("Modifying") || source.contains("nativeQuery"))
                      && ORDER_WRITE_TARGETS.stream().anyMatch(source::contains);
                })
            .map(Path::toString)
            .toList();

    assertThat(offenders).as("一括更新は集約の行為メソッドを通らないため、合計を取り直さずに行や列を動かせる").isEmpty();
  }

  private static Stream<Path> javaSources() throws IOException {
    return Files.walk(MAIN_SOURCES).filter(path -> path.toString().endsWith(".java"));
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new IllegalStateException("ソースを読めませんでした: " + path, e);
    }
  }
}
