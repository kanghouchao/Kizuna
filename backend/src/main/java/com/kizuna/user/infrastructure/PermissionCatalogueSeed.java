package com.kizuna.user.infrastructure;

import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.SystemRole;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.DatabaseException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

/**
 * 権限目録（{@code t_permissions}）と既定ロールの行（{@code t_roles}）を {@link PermissionCode} と {@link SystemRole}
 * の宣言から播種する Liquibase 変更。changeset は {@code runAlways} で毎回走る。
 *
 * <p>既定ロールへの授与（{@code t_role_permissions}）は<b>ロール行を初めて作った回だけ</b>宣言の {@code defaultRoles} を播種する。
 * 既定ロールの権限は管理者が編集できるため、既存行の授与を起動のたびに取り直すと運用の編集を黙って巻き戻してしまう。 基線 seed
 * が全目録行と全既定授与を持つので、通常の起動でこの播種が授与を書くことはない。
 *
 * <p>目録行は挿入のみで削除しない。目録行は自作ロールからも参照されるため、廃止は利用者のロールから権限を落とす判断を伴い、明示的な移行 changeset に属する（{@code
 * fk_t_role_permissions_permission} は RESTRICT で、参照中の削除を拒否する）。
 *
 * <p>Liquibase が直接生成する（Spring の管理外）ため依存注入は使えず、changelog のロック下で生の JDBC を扱う。
 */
public class PermissionCatalogueSeed implements CustomTaskChange {

  private static final String INSERT_PERMISSION =
      "INSERT INTO t_permissions (code) VALUES (?) ON CONFLICT (code) DO NOTHING";

  private static final String INSERT_GRANT =
      "INSERT INTO t_role_permissions (role_id, permission_id)"
          + " SELECT r.id, p.id FROM t_roles r, t_permissions p"
          + " WHERE r.name = ? AND r.is_system = TRUE AND p.code = ?"
          + " ON CONFLICT DO NOTHING";

  private static final String INSERT_SYSTEM_ROLE =
      "INSERT INTO t_roles (name, is_system) VALUES (?, TRUE) ON CONFLICT (name) DO NOTHING";

  private static final String SELECT_SYSTEM_ROLE_NAMES =
      "SELECT name FROM t_roles WHERE is_system = TRUE";

  private int insertedPermissions;
  private int insertedRoles;
  private int insertedGrants;

  @Override
  public void execute(Database database) throws CustomChangeException {
    JdbcConnection connection = (JdbcConnection) database.getConnection();
    try {
      insertedPermissions = insertMissingPermissions(connection);
      Set<SystemRole> created = ensureSystemRoles(connection);
      insertedRoles = created.size();
      insertedGrants = insertInitialGrants(connection, created);
    } catch (DatabaseException | SQLException ex) {
      throw new CustomChangeException("権限目録の播種に失敗しました", ex);
    }
  }

  private static int insertMissingPermissions(JdbcConnection connection)
      throws DatabaseException, SQLException {
    try (PreparedStatement statement = connection.prepareStatement(INSERT_PERMISSION)) {
      for (PermissionCode code : PermissionCode.values()) {
        statement.setString(1, code.name());
        statement.addBatch();
      }
      return countAffected(statement.executeBatch());
    }
  }

  /**
   * 宣言された既定ロールの行を揃え、この回に新しく作った行を返す。ロール名の正本も {@link SystemRole} 側にあるため、欠けている行はここで作る — 別の changeset
   * に任せると、毎回走る本播種のほうが先に動いて「まだ存在しないロール」で止まってしまう。
   *
   * <p>落とす場合が 2 つある。同名の行が利用者自作ロール（{@code is_system = FALSE}）として既にある場合は、既定ロールとして扱えば利用者のロールを奪い、
   * 扱わなければ授与が 0 行で黙って抜けるため、人の判断が要る。行を新規に作ったうえで宣言に無い既定ロールの行が残っている場合（改名や廃止の形）も落とす — 名称は {@code
   * t_user_roles} の参照が繋がっている自然キーなので、新しい名前の行を足すだけでは利用者は旧行に取り残される。
   *
   * <p>新規作成が無いときの宣言に無い行は許す。新しい版が足したロールが、旧版へ巻き戻した実体から見えているだけの状態であり、ここで落とすと巻き戻しが起動不能になる。
   */
  private static Set<SystemRole> ensureSystemRoles(JdbcConnection connection)
      throws DatabaseException, SQLException, CustomChangeException {
    Set<SystemRole> created = EnumSet.noneOf(SystemRole.class);
    try (PreparedStatement statement = connection.prepareStatement(INSERT_SYSTEM_ROLE)) {
      for (SystemRole role : SystemRole.values()) {
        statement.setString(1, role.getRoleName());
        if (statement.executeUpdate() > 0) {
          created.add(role);
        }
      }
    }

    Set<SystemRole> missing = EnumSet.allOf(SystemRole.class);
    List<String> undeclared = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(SELECT_SYSTEM_ROLE_NAMES);
        ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        String name = rows.getString(1);
        if (!missing.removeIf(role -> role.getRoleName().equals(name))) {
          undeclared.add(name);
        }
      }
    }
    if (!missing.isEmpty()) {
      throw new CustomChangeException(
          "同名の利用者自作ロールがあるため平台既定ロールを用意できません: "
              + missing.stream().map(SystemRole::getRoleName).toList());
    }
    if (!created.isEmpty() && !undeclared.isEmpty()) {
      throw new CustomChangeException(
          "宣言に無い既定ロールの行が残っています: "
              + undeclared
              + "。改名・廃止は既存行の移行 changeset で行ってください（新しい名前の行を足すだけでは利用者が旧行に取り残されます）");
    }
    return created;
  }

  /** この回に作った既定ロールにだけ初期授与を播種する。既存行の授与は管理者の編集の結果なので触らない。 */
  private static int insertInitialGrants(JdbcConnection connection, Set<SystemRole> created)
      throws DatabaseException, SQLException {
    if (created.isEmpty()) {
      return 0;
    }
    try (PreparedStatement statement = connection.prepareStatement(INSERT_GRANT)) {
      for (PermissionCode code : PermissionCode.values()) {
        for (SystemRole role : code.getDefaultRoles()) {
          if (!created.contains(role)) {
            continue;
          }
          statement.setString(1, role.getRoleName());
          statement.setString(2, code.name());
          statement.addBatch();
        }
      }
      return countAffected(statement.executeBatch());
    }
  }

  /**
   * バッチの更新件数を合計する。件数を返さないドライバの負値（{@code SUCCESS_NO_INFO}）は 0 として数えるため、確認メッセージ用の目安であり整合性の保証ではない —
   * 整合は挿入文の冪等性そのものが担う。
   */
  private static int countAffected(int[] updateCounts) {
    return Arrays.stream(updateCounts).filter(count -> count > 0).sum();
  }

  @Override
  public String getConfirmationMessage() {
    return "権限目録 %d 行・既定ロール %d 行を追加し、新規の既定ロールへ初期授与 %d 行を播種しました"
        .formatted(insertedPermissions, insertedRoles, insertedGrants);
  }

  @Override
  public void setUp() throws SetupException {
    // 事前準備は要らない（宣言は enum から読む）。
  }

  @Override
  public void setFileOpener(ResourceAccessor resourceAccessor) {
    // changelog 以外の資源を読まない。
  }

  @Override
  public ValidationErrors validate(Database database) {
    return new ValidationErrors();
  }
}
