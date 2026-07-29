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
 * 権限目録（{@code t_permissions}）・平台既定ロールの行（{@code t_roles}）・既定ロールへの授与（{@code t_role_permissions}）を
 * {@link PermissionCode} と {@link SystemRole} の宣言から播種する Liquibase 変更。
 *
 * <p>権限はコードと生命周期を共にするため、目録行と既定授与を changeset へ手書きすると「enum に足したが播種を忘れた」形の齟齬が生まれる。宣言を唯一の源にして
 * その欠陥類を構造的に消すのがこの変更の役目であり、changeset は {@code runAlways} で毎回この写像を取り直す。
 *
 * <p>既定ロールの授与は宣言との完全な写像にする（不足を挿し、宣言に無いものを取り消す）。既定ロールは API から改廃できないため授与の出所は宣言だけであり、取り下げを別の一度きりの
 * changeset に委ねると毎回走る本播種が次の起動でそれを挿し戻してしまう。取り消しは {@code is_system = TRUE}
 * かつ「この版が知っている権限」に絞るため、利用者自作ロールの授与にも、 新しい版が足した権限の授与にも触れない（旧版へ巻き戻した実体が新版の授権を剥がさない）。
 *
 * <p>目録行（{@code t_permissions}）は挿入のみで削除しない。目録行は自作ロールからも参照されるため、廃止は利用者のロールから権限を落とす判断を伴い、明示的な移行
 * changeset に属する（{@code fk_t_role_permissions_permission} は RESTRICT で、参照中の削除を拒否する）。
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

  /**
   * 宣言に無い授与の取り消し。対象は既定ロールのみで、利用者自作ロールの授与には触れない。
   *
   * <p>取り消す範囲は「この版が知っている権限」に限る（{@code = ANY (known)}）。旧版へ巻き戻した実体が、新しい版が足した権限の授与を —
   * 自分はその権限の存在すら知らないまま — 剥がしてしまうのを防ぐ。
   */
  private static final String DELETE_UNDECLARED_GRANT =
      "DELETE FROM t_role_permissions rp USING t_roles r, t_permissions p"
          + " WHERE rp.role_id = r.id AND rp.permission_id = p.id"
          + " AND r.is_system = TRUE AND r.name = ?"
          + " AND p.code = ANY (?) AND p.code <> ALL (?)";

  private static final String INSERT_SYSTEM_ROLE =
      "INSERT INTO t_roles (name, is_system) VALUES (?, TRUE) ON CONFLICT (name) DO NOTHING";

  private static final String SELECT_SYSTEM_ROLE_NAMES =
      "SELECT name FROM t_roles WHERE is_system = TRUE";

  private int insertedPermissions;
  private int insertedRoles;
  private int insertedGrants;
  private int revokedGrants;

  @Override
  public void execute(Database database) throws CustomChangeException {
    JdbcConnection connection = (JdbcConnection) database.getConnection();
    try {
      insertedPermissions = insertMissingPermissions(connection);
      insertedRoles = ensureSystemRoles(connection);
      insertedGrants = insertMissingGrants(connection);
      revokedGrants = deleteUndeclaredGrants(connection);
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
   * 宣言された平台既定ロールの行を揃える。ロール名の正本も {@link SystemRole} 側にあるため、欠けている行はここで作る — 別の changeset
   * に任せると、毎回走る本播種のほうが先に動いて「まだ存在しないロール」で止まってしまう。
   *
   * <p>落とす場合が 2 つある。
   *
   * <p>1 つは、同名の行が利用者自作ロール（{@code is_system = FALSE}）として既にある場合。既定ロールとして扱えば利用者のロールを奪い、扱わなければ授与が 0
   * 行で黙って抜けるため、人の判断が要る。
   *
   * <p>もう 1 つは、行を新規に作ったうえで宣言に無い既定ロールの行が残っている場合 — 改名や廃止の形である。名称は {@code t_user_roles}
   * の参照が繋がっている自然キーなので、新しい名前の行を足すだけでは利用者は旧行に取り残され、授与の増減もその行に届かない。行の移行は明示的な changeset に属する。
   *
   * <p>新規作成が無い（{@code inserted ==
   * 0}）ときの宣言に無い行は許す。新しい版が足したロールが、旧版へ巻き戻した実体から見えているだけの状態であり、ここで落とすと巻き戻しが起動不能になる。
   */
  private static int ensureSystemRoles(JdbcConnection connection)
      throws DatabaseException, SQLException, CustomChangeException {
    int inserted;
    try (PreparedStatement statement = connection.prepareStatement(INSERT_SYSTEM_ROLE)) {
      for (SystemRole role : SystemRole.values()) {
        statement.setString(1, role.getRoleName());
        statement.addBatch();
      }
      inserted = countAffected(statement.executeBatch());
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
    if (inserted > 0 && !undeclared.isEmpty()) {
      throw new CustomChangeException(
          "宣言に無い既定ロールの行が残っています: "
              + undeclared
              + "。改名・廃止は既存行の移行 changeset で行ってください（新しい名前の行を足すだけでは利用者が旧行に取り残されます）");
    }
    return inserted;
  }

  private static int insertMissingGrants(JdbcConnection connection)
      throws DatabaseException, SQLException {
    try (PreparedStatement statement = connection.prepareStatement(INSERT_GRANT)) {
      for (PermissionCode code : PermissionCode.values()) {
        for (SystemRole role : code.getDefaultRoles()) {
          statement.setString(1, role.getRoleName());
          statement.setString(2, code.name());
          statement.addBatch();
        }
      }
      return countAffected(statement.executeBatch());
    }
  }

  /**
   * 既定ロールに残る「宣言に無い授与」を取り消す。既定ロールの権限集合はコード宣言の写像でしかありえないため、取り下げも宣言の編集だけで完結させる — 取り消しを別の一度きりの
   * changeset に委ねると、毎回走る本播種が次の起動でそれを黙って挿し戻してしまう。
   *
   * <p>対象は既定ロール（{@code is_system = TRUE}）かつこの版が知っている権限に限る（{@link
   * #DELETE_UNDECLARED_GRANT}）ため、利用者自作ロールの授与と、 新しい版が足した権限の授与は残る。
   */
  private static int deleteUndeclaredGrants(JdbcConnection connection)
      throws DatabaseException, SQLException {
    String[] known =
        Arrays.stream(PermissionCode.values()).map(PermissionCode::name).toArray(String[]::new);
    int revoked = 0;
    try (PreparedStatement statement = connection.prepareStatement(DELETE_UNDECLARED_GRANT)) {
      for (SystemRole role : SystemRole.values()) {
        String[] declared =
            Arrays.stream(PermissionCode.values())
                .filter(code -> code.getDefaultRoles().contains(role))
                .map(PermissionCode::name)
                .toArray(String[]::new);
        statement.setString(1, role.getRoleName());
        statement.setArray(2, connection.getUnderlyingConnection().createArrayOf("text", known));
        statement.setArray(3, connection.getUnderlyingConnection().createArrayOf("text", declared));
        revoked += statement.executeUpdate();
      }
    }
    return revoked;
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
    return "権限目録 %d 行・既定ロール %d 行・既定ロールへの授与 %d 行を追加し、宣言に無い授与 %d 行を取り消しました"
        .formatted(insertedPermissions, insertedRoles, insertedGrants, revokedGrants);
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
