package com.kizuna.customer.domain;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository
    extends JpaRepository<Customer, String>, JpaSpecificationExecutor<Customer> {

  /**
   * 顧客を悲観排他ロック（SELECT ... FOR UPDATE）付きで取得する。エンティティ名は HQL の予約語衝突を避けるため FQCN で参照する。
   *
   * <p>顧客の紐づけを読み書きする経路の直列化点。紐づけ・変更・解除（{@code CustomerMemberLinkService#link}／{@code
   * CustomerMemberLinkService#unlink}）と、台帳への記帳（受注完了・手動調整）は、いずれも先にこの行を押さえてから紐づけを解決する。
   * 押さえずに解決すると記帳と解除が何も競合せずに双方 commit でき、受注は取り消せないまま、利用と付与だけが解除済みの会員に残る。
   *
   * <p>押さえるのが紐づけ行ではなく顧客行なのは、紐づけ行が置き換わるため。変更は旧行の RELEASED 化と新行の INSERT で成り立つので、
   * 紐づけ行を待つ形では、待たされた問い合わせが置換のコミット後に自分のスナップショットで述語を評価し直す — RELEASED になった旧行は落ち、
   * 置換で入った新行はそのスナップショットに無い。どちらの会員でもない「非会員」として完了してしまい、この結末はどの直列順序にも対応しない。
   * 顧客行なら置換の前後で同一なので、ロック取得後の紐づけ照会は新しい文として走り、READ COMMITTED の既定でも その時点でコミット済みの行（置換後の新しい ACTIVE
   * 行を含む）を必ず見る。
   *
   * <p>会員側の排他はこのロックの射程外。同じ会員を別々の顧客へ紐づける競合は押さえる顧客行が異なるため直列化されず、 防いでいるのは部分一意索引（member_id WHERE
   * status='ACTIVE'）の側で、そこからの一意違反が 409 になる。
   *
   * <p>受注完了は顧客行を引けなくても（行が無い・他店舗で storeFilter に落ちる）そのまま非会員として進む。既にその顧客の紐づけも
   * 引けない状態であり、どの会員にも誤って積まない形なので、この境界は許容する。
   *
   * <p>ロック順序は顧客行 →（紐づけ行の UPDATE／INSERT）→ 台帳の加算ロット（{@code
   * PointEntryRepository#findCreditsForUpdate}）。記帳の 2 経路で順序が揃い、解除は台帳を触らないため待ちが環にならない。
   * 読むだけの経路（残高照会・完了の事前計算・紐づけ履歴・顧客の投影）はこのロックを取らない。
   *
   * <p>Customer は StoreScopedEntity で、この問い合わせにも {@code storeFilter} が掛かる（applyToLoadByKey により派生の
   * {@code findById} でも同様 — StoreScopedLoadByKeyIT が実測で固定）。{@code @StoreScoped} の文脈では
   * 他店舗の顧客が空になり、存在の有無が漏れない。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from com.kizuna.customer.domain.Customer c where c.id = :id")
  Optional<Customer> findByIdForUpdate(@Param("id") String id);

  /**
   * {@link #findByIdForUpdate} と同じロックを、待たずに取れるときだけ取る（{@code FOR UPDATE NOWAIT}）。取れなければ {@code
   * CannotAcquireLockException}。
   *
   * <p>行を押さえたまま次の行を待つ経路で使う。統合は存続行と被統合行を押さえた後で、被統合行を指す墓標を圧平（＝墓標の行を押さえる）するため、
   * 「墓標を押さえたまま統合先を待つ」形は統合と待ちが環になる。待たない取得なら環は生まれず、取れなかった競合は案内の読める応答に写せる。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
  @Query("select c from com.kizuna.customer.domain.Customer c where c.id = :id")
  Optional<Customer> findByIdForUpdateNoWait(@Param("id") String id);

  /**
   * 旧 ID を渡されても統合先の生きた行を返す読み口。統合先参照の解決と行の取得を 1 文で行う。
   *
   * <p>2 文に分けると、READ COMMITTED では文ごとに異なるスナップショットを見るため、統合先を読んだ後に連鎖統合が確定すると 既に墓標になった行を本体として返してしまう。1
   * 文なら解決と取得が同じスナップショットで、返る行は必ず生きている。
   */
  @Query(
      """
      select c from Customer c
      where c.id = coalesce(
        (select m.mergedIntoId from Customer m where m.id = :id), :id)
      """)
  Optional<Customer> findResolvingMerge(@Param("id") String id);

  String DUPLICATE_PHONE_SELECT =
      """
    select p.value as phoneNumber, count(c) as total
    from Customer c
    join CustomerContact p on p.customerId = c.id
    where c.mergedIntoId is null and p.deleted = false and p.preferred = true and p.type = 'PHONE'
    """;
  String DUPLICATE_PHONE_GROUP_ORDER = " group by p.value having count(c) >= 2 order by p.value";

  @Query(DUPLICATE_PHONE_SELECT + DUPLICATE_PHONE_GROUP_ORDER)
  List<CustomerDuplicateGroupView> findDuplicatePhoneNumbers(Limit limit);

  @Query(DUPLICATE_PHONE_SELECT + " and p.value > :cursor" + DUPLICATE_PHONE_GROUP_ORDER)
  List<CustomerDuplicateGroupView> findDuplicatePhoneNumbersAfter(String cursor, Limit limit);

  @Query(
      """
    select c from Customer c
    join CustomerContact p on p.customerId = c.id
    where c.mergedIntoId is null and p.deleted = false and p.preferred = true and p.type = 'PHONE'
      and p.value in :phoneNumbers order by p.value, c.id
    """)
  List<Customer> findByPreferredPhones(Collection<String> phoneNumbers);

  /**
   * 名指された顧客のうち、生きている行だけを返す。統合の前に 2 行を見比べる読み口の入口。
   *
   * <p>墓標を落とすのは、見比べの答えが「この 2 行は同一人物か」だからである。片方が既に墓標なら見比べる対象は もう存在せず、行を返せば「まだ畳める」と読ませることになる。店舗の絞り込みは
   * {@code storeFilter} が担う。
   */
  List<Customer> findByIdInAndMergedIntoIdIsNull(Collection<String> ids);

  /**
   * その顧客の統合先。生きている行と存在しない行はどちらも空で返る — 呼出側はどちらの場合も「渡された ID がそのまま着地点」として扱うため、区別する必要がない。
   *
   * <p>押さえた行の実体からではなく別問い合わせで読む。悲観排他ロックは既に永続化文脈に載っている実体の状態を更新しないため、
   * ロック後に実体のフィールドを読むと第一次キャッシュの古い値を見る（{@link #findByIdForUpdate} の契約）。投影は実体を経由しないので、 押さえた直後の DB
   * の値をそのまま返す。
   */
  @Query(
      """
      select c.mergedIntoId from Customer c
      where c.id = :id
      """)
  Optional<String> findMergedIntoId(@Param("id") String id);

  /** その顧客が既に墓標（統合済み）かどうか。統合先そのものは要らないが、判定の出所は一つに保つ。 */
  default boolean isMerged(String id) {
    return findMergedIntoId(id).isPresent();
  }

  /**
   * 連鎖統合の圧平。被統合行を指していた既存の墓標を、新しい統合先へ付け替える。A→B の後で B→C を統合すると A は直接 C を指し、旧 ID の解決は常に一跳で届く（ADR
   * 0010）。
   *
   * <p>WHERE 句に店舗を明示するのは、Hibernate の {@code @Filter} が HQL の一括 UPDATE には掛からないため。
   *
   * <p>{@code versioned} を付けるのは、統合先参照が可変列であり、この文が版を上げないと並行する顧客更新の 書き戻しが圧平を静かに取り消すため。墓標 A を読んだ更新の途中で
   * B→C の統合が A を C へ付け替えると、 更新側は自分が読んだ時点の値（B）を他の項目と一緒に flush する — 版が据え置きなら楽観ロックの述語が 成立してしまい、A→B→C
   * の連鎖が復活して「解決は常に一跳」が破れる。版を上げれば敗者は 409 になる。
   *
   * @return 付け替えた墓標の件数
   */
  @Modifying
  @Query(
      """
      update versioned Customer c set c.mergedIntoId = :survivingId
      where c.mergedIntoId = :mergedId and c.storeId = :storeId
      """)
  int flattenMergedInto(
      @Param("survivingId") String survivingId,
      @Param("mergedId") String mergedId,
      @Param("storeId") Long storeId);
}
