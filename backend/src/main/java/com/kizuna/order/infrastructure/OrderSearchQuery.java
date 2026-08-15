package com.kizuna.order.infrastructure;

import com.kizuna.order.domain.OrderQueryCriteria;
import com.kizuna.order.domain.OrderSortKey;
import com.kizuna.shared.web.PageCursor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.stereotype.Component;

/**
 * 受注一覧の抽出条件から、条件に合う受注 <b>ID の並び</b>だけを引く。
 *
 * <p>行の中身をここで引かないのは、表示に要る顧客名・キャスト名・受付担当名が別の集約にあり、読み側 projection（{@code OrderView}）の join
 * がその組み立てを既に一手に引き受けているため。条件と並びは要求ごとに変わるので動的に組み立てる必要がある一方、 選択する列は変わらない — その 2
 * つを同じ問い合わせに押し込むと、列の一覧が動的な文字列の中に写し取られ、列が増えたときに 片方だけが更新される。ID を引いてから既存の読み口で引き直せば、列の一覧は 1 箇所に残る。
 *
 * <p>条件を文字列で組み立てるのは、省略された条件の述語を<b>生成しない</b>ため。JPQL の {@code ":param is null or ..."} は PostgreSQL の
 * null パラメータ型推論で実行時に落ちる（{@code CustomerService.searchSpec} と同じ判断）。並び替えの式は {@link OrderSortKey}
 * の列挙だけから来るので、要求の値が SQL に混ざる余地は無い。
 */
@Component
@RequiredArgsConstructor
public class OrderSearchQuery {

  /**
   * LIKE パターンのエスケープ規則。既定の {@code \} ではなく {@code !} を使うのは、JPQL の文字列リテラルに 現れる {@code \}
   * の解釈を読み手に確かめさせないため（派生クエリと違い、ここは {@code escape} 句まで手で書く）。
   */
  private static final EscapeCharacter LIKE_ESCAPE = EscapeCharacter.of('!');

  private static final String FROM_CLAUSE =
      """
      from com.kizuna.order.domain.Order o
        left join com.kizuna.customer.domain.Customer c on c.id = o.customerId
      """;

  private final EntityManager entityManager;

  /**
   * 並びの中の 1 行。ID と、その行を並べるのに使った鍵の値の組。
   *
   * <p>鍵を<b>この問い合わせから</b>返すのが要点で、続きを指すカーソルはこの値だけから組む。行の中身を引く 2 本目の問い合わせから鍵を読むと、READ COMMITTED では 2
   * 本が別の断面を見るため、間に他の操作者が境界の行の 鍵を書き換えると、並べたときの値と続きに書く値が食い違う — 続きはその新しい値の後ろから始まり、間に挟まる 受注を丸ごと飛ばす（人数 1
   * の行が 100 に直されれば、1〜100 の受注が続きに現れない）。
   */
  public record OrderedRow(String id, Object sortKey) {}

  /**
   * 作業キューの並び（カーソル型）。渡された位置より後ろだけを、上限まで返す。
   *
   * <p>位置は「何件目か」ではなく並びの鍵そのものなので、確定・取消で手前の行が消えても後続は繰り上がらない。
   *
   * @param cursor 続きの位置。null なら先頭から
   * @param limit 取得件数の上限（呼出側が「続きの有無」の判定ぶんを足して渡す）
   */
  public List<OrderedRow> findRows(OrderQueryCriteria criteria, PageCursor cursor, int limit) {
    List<String> conditions = new ArrayList<>(baseConditions(criteria));
    if (cursor != null) {
      conditions.add(keysetCondition(criteria));
    }
    TypedQuery<Object[]> query =
        entityManager.createQuery(
            "select o.id, %s ".formatted(criteria.sortKey().expression())
                + FROM_CLAUSE
                + where(conditions)
                + orderBy(criteria),
            Object[].class);
    bindBase(query, criteria);
    if (cursor != null) {
      bindCursor(query, criteria, cursor);
    }
    return query.setMaxResults(limit).getResultList().stream()
        .map(row -> new OrderedRow((String) row[0], row[1]))
        .toList();
  }

  /** アーカイブの並び（オフセット型）。増えるだけの記録なので、位置をページ番号で指せる。 */
  public List<String> findIds(OrderQueryCriteria criteria, Pageable pageable) {
    TypedQuery<String> query =
        entityManager.createQuery(
            "select o.id " + FROM_CLAUSE + where(baseConditions(criteria)) + orderBy(criteria),
            String.class);
    bindBase(query, criteria);
    return query
        .setFirstResult((int) pageable.getOffset())
        .setMaxResults(pageable.getPageSize())
        .getResultList();
  }

  /** アーカイブの総件数。ページャが最終ページを出すために要る。 */
  public long count(OrderQueryCriteria criteria) {
    TypedQuery<Long> query =
        entityManager.createQuery(
            "select count(o.id) " + FROM_CLAUSE + where(baseConditions(criteria)), Long.class);
    bindBase(query, criteria);
    return query.getSingleResult();
  }

  private List<String> baseConditions(OrderQueryCriteria criteria) {
    List<String> conditions = new ArrayList<>();
    conditions.add("o.status in :statuses");
    if (criteria.customerName() != null) {
      // 表示と同じ規則で照合する — 台帳の顧客名が無ければ受付で録入された連絡先の氏名、それも無ければ
      // 申請時の名乗りで呼ぶ。当店に台帳行の無い会員の未確定申請は前 2 つをどちらも持たないため、
      // 名乗りまで見ないと画面に「お客様名なし」として出ている行が検索で消える。3 つが同時に埋まることはない。
      conditions.add(
          "lower(coalesce(c.name, o.contactName, o.requesterDeclaredName)) like :customerName escape '"
              + LIKE_ESCAPE.getEscapeCharacter()
              + "'");
    }
    if (criteria.businessDate() != null) {
      conditions.add("o.businessDate = :businessDate");
    }
    return conditions;
  }

  /**
   * カーソルの比較。並びと同じ式・同じ向きで、副キー id まで見て 1 行を一意に指す。
   *
   * <p>鍵の値は {@link OrderSortKey} が未設定を番兵へ均すので、比較に null が現れることはない。
   */
  private String keysetCondition(OrderQueryCriteria criteria) {
    String key = criteria.sortKey().expression();
    String comparison = criteria.descending() ? "<" : ">";
    return "(%s %s :cursorKey or (%s = :cursorKey and o.id %s :cursorId))"
        .formatted(key, comparison, key, comparison);
  }

  private String orderBy(OrderQueryCriteria criteria) {
    String direction = criteria.descending() ? "desc" : "asc";
    // 副キー id まで固定して全順序にする。一意な副キーが無いと、カーソルが 1 行を指せないだけでなく
    // オフセットのページ送りでも行の重複と取りこぼしが起きる。
    return " order by %s %s, o.id %s"
        .formatted(criteria.sortKey().expression(), direction, direction);
  }

  private String where(List<String> conditions) {
    return " where " + String.join(" and ", conditions);
  }

  private void bindBase(TypedQuery<?> query, OrderQueryCriteria criteria) {
    query.setParameter("statuses", criteria.statuses());
    if (criteria.customerName() != null) {
      query.setParameter(
          "customerName", "%" + LIKE_ESCAPE.escape(criteria.customerName().toLowerCase()) + "%");
    }
    if (criteria.businessDate() != null) {
      query.setParameter("businessDate", criteria.businessDate());
    }
  }

  private void bindCursor(TypedQuery<?> query, OrderQueryCriteria criteria, PageCursor cursor) {
    Object key =
        switch (criteria.sortKey().keyType()) {
          case DATE -> cursor.dateKey();
          case NUMBER -> cursor.numberKey();
        };
    query.setParameter("cursorKey", key);
    query.setParameter("cursorId", cursor.id());
  }
}
