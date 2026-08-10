package com.kizuna.shared.exception;

/**
 * 業務例外へ写像する対象となる DB 制約の目録。字面は DDL（changelog）の制約名そのもので、{@link IntegrityViolations} が Hibernate
 * の報告する制約名と等値比較する。
 *
 * <p>収録するのは実際に写像している制約だけで、DDL 上の全制約は載せない — 写像先を持たない制約は実装欠陥として大きく失敗させる側であり、 ここに現れると「扱える違反」に見えてしまう。
 *
 * <p>各成員の字面が DDL に実在することは適応度テスト（{@code DbConstraintLiteralTests}）が機械検証する。
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

  /** t_point_entries.idempotency_key の一意制約（ADR 0007）。 */
  UQ_T_POINT_ENTRIES_IDEMPOTENCY_KEY("uq_t_point_entries_idempotency_key");

  private final String sqlName;

  DbConstraint(String sqlName) {
    this.sqlName = sqlName;
  }

  /** DDL 上の制約名。 */
  public String sqlName() {
    return sqlName;
  }
}
