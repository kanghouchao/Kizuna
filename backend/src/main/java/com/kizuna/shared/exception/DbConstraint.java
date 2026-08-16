package com.kizuna.shared.exception;

/**
 * 業務例外へ写像する対象となる DB 制約の目録。字面は DDL（changelog）の制約名そのもので、{@link IntegrityViolations} が Hibernate
 * の報告する制約名と等値比較する。
 *
 * <p>収録するのは実際に写像している制約だけで、DDL 上の全制約は載せない — 写像先を持たない制約は実装欠陥として大きく失敗させる側であり、 ここに現れると「扱える違反」に見えてしまう。
 *
 * <p>ここに載せる外部キーは DDL 側で {@code NO ACTION} を宣言する（{@code RESTRICT} ではなく）。参照を止める力は同じだが、 PostgreSQL は
 * RESTRICT 違反を SQLSTATE 23001 で報告し、その文言から Hibernate は制約名を取り出せない（{@code constraint=null}） —
 * 名前で写す本目録が決して命中せず、削除の拒否が 500 に化ける。
 *
 * <p>各成員の字面が DDL に実在することと、上の NO ACTION 規律は適応度テスト（{@code DbConstraintLiteralTests}）が機械検証する。
 */
public enum DbConstraint {

  /** t_users.email の一意制約。 */
  UQ_T_USERS_EMAIL("uq_t_users_email"),

  /** t_users.line_user_id の一意制約。 */
  UQ_T_USERS_LINE_USER_ID("uq_t_users_line_user_id"),

  /** t_roles.name の一意制約。 */
  UQ_T_ROLES_NAME("uq_t_roles_name"),

  /** t_user_roles → t_roles の FK（RESTRICT）。 */
  FK_T_USER_ROLES_ROLE("fk_t_user_roles_role"),

  /** t_user_stores → t_stores の FK。 */
  FK_T_USER_STORES_STORE("fk_t_user_stores_store"),

  /** t_cast_invitations → t_casts の FK。 */
  FK_T_CAST_INVITATIONS_CAST("fk_t_cast_invitations_cast"),

  /** t_orders → t_casts の FK（RESTRICT）。受注が担当キャストを指したまま消えないようにする。 */
  FK_T_ORDERS_CAST("fk_t_orders_cast"),

  /** t_orders → t_customers の FK（RESTRICT）。受注が来店した顧客を指したまま消えないようにする。 */
  FK_T_ORDERS_CUSTOMER("fk_t_orders_customer"),

  /** t_point_entries.idempotency_key の一意制約（ADR 0007）。 */
  UQ_T_POINT_ENTRIES_IDEMPOTENCY_KEY("uq_t_point_entries_idempotency_key"),

  /** t_customer_member_links の「店舗ごと会員 1 人につき有効な関連は高々 1 本」の部分一意索引。 */
  UQ_T_CUSTOMER_MEMBER_LINKS_ACTIVE_MEMBER("uq_t_customer_member_links_active_member"),

  /** t_customer_member_links の「顧客 1 行につき有効な関連は高々 1 本」の部分一意索引。 */
  UQ_T_CUSTOMER_MEMBER_LINKS_ACTIVE_CUSTOMER("uq_t_customer_member_links_active_customer"),

  /** t_customer_merges → t_customers（存続行）の FK（RESTRICT）。 */
  FK_T_CUSTOMER_MERGES_SURVIVING("fk_t_customer_merges_surviving"),

  /** t_customer_merges → t_customers（被統合行）の FK（RESTRICT）。 */
  FK_T_CUSTOMER_MERGES_MERGED("fk_t_customer_merges_merged"),

  /** t_customers.merged_into_id の自己参照 FK（RESTRICT）。墓標の指す先が消えると旧 ID の解決が切れる。 */
  FK_T_CUSTOMERS_MERGED_INTO("fk_t_customers_merged_into");

  private final String sqlName;

  DbConstraint(String sqlName) {
    this.sqlName = sqlName;
  }

  /** DDL 上の制約名。 */
  public String sqlName() {
    return sqlName;
  }
}
