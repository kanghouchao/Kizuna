package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionTree;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * 基線 seed のメニュー（{@code seed/02-menus.yaml}）が、グループと店舗の二つの木の形を保つことを固定する。項目が開く権限は項目の木に属し、
 * 区分見出しは権限の区分（{@link com.kizuna.user.domain.PermissionGroup}）と同じ名を持つ。
 */
class MenuSeedTreeTests {

  private static final Path MENUS_SEED =
      Paths.get("src/main/resources/db/changelog/releases/v0.1.0/seed/02-menus.yaml");

  private record Row(
      String id, String parentId, String tree, String label, String path, String permission) {}

  private static Map<String, Row> rows;

  @BeforeAll
  @SuppressWarnings("unchecked")
  static void readSeed() throws Exception {
    Map<String, Object> changelog;
    try (Reader reader = Files.newBufferedReader(MENUS_SEED)) {
      changelog = new Yaml().load(reader);
    }
    rows = new HashMap<>();
    for (Object entry : (List<Object>) changelog.get("databaseChangeLog")) {
      Map<String, Object> changeSet =
          (Map<String, Object>) ((Map<String, Object>) entry).get("changeSet");
      for (Object change : (List<Object>) changeSet.get("changes")) {
        Map<String, Object> insert =
            (Map<String, Object>) ((Map<String, Object>) change).get("insert");
        Map<String, String> values = new HashMap<>();
        for (Object column : (List<Object>) insert.get("columns")) {
          Map<String, Object> c =
              (Map<String, Object>) ((Map<String, Object>) column).get("column");
          Object value = c.containsKey("value") ? c.get("value") : c.get("valueNumeric");
          values.put((String) c.get("name"), String.valueOf(value));
        }
        Row row =
            new Row(
                values.get("id"),
                values.get("parent_id"),
                values.get("tree"),
                values.get("label"),
                values.get("path"),
                values.get("permission"));
        rows.put(row.id(), row);
      }
    }
    assertThat(rows).as("seed の t_menus 行").isNotEmpty();
  }

  private static PermissionTree treeOf(Row row) {
    Row root = row.parentId() == null ? row : rows.get(row.parentId());
    return PermissionTree.valueOf(root.tree());
  }

  @Test
  void 木は根の行だけが持ちグループか店舗のどちらかである() {
    for (Row row : rows.values()) {
      if (row.parentId() == null) {
        assertThat(row.tree()).as(row.id()).isIn("GROUP", "STORE");
      } else {
        assertThat(row.tree()).as(row.id()).isNull();
        assertThat(rows.get(row.parentId()).parentId()).as(row.id() + " は 2 階層目").isNull();
      }
    }
  }

  @Test
  void 項目が開く権限は項目の木に属する() {
    List<String> mismatches = new ArrayList<>();
    for (Row row : rows.values()) {
      if (row.permission() != null
          && PermissionCode.valueOf(row.permission()).getTree() != treeOf(row)) {
        mismatches.add(row.id() + " (" + row.permission() + ")");
      }
    }
    assertThat(mismatches).isEmpty();
  }

  @Test
  void 区分見出しは配下の項目の権限の区分と同じ名を持つ() {
    for (Row row : rows.values()) {
      if (row.parentId() != null && row.permission() != null) {
        assertThat(rows.get(row.parentId()).label())
            .as(row.id())
            .isEqualTo(PermissionCode.valueOf(row.permission()).getGroup().getLabel());
      }
    }
  }

  @Test
  void どちらの木もダッシュボードを根の項目として一つずつ持つ() {
    for (PermissionTree tree : PermissionTree.values()) {
      List<Row> rootLeaves =
          rows.values().stream()
              .filter(row -> row.parentId() == null && tree.name().equals(row.tree()))
              .filter(row -> row.path() != null)
              .toList();
      assertThat(rootLeaves).as(tree.name()).extracting(Row::label).containsExactly("ダッシュボード");
      assertThat(rootLeaves.getFirst().permission()).as(tree.name()).isNull();
    }
  }

  @Test
  void グループの木は管理画面の絶対パスを店舗の木は店舗の中のパスを持つ() {
    for (Row row : rows.values()) {
      if (row.path() == null) continue;
      if (treeOf(row) == PermissionTree.GROUP) {
        assertThat(row.path()).as(row.id()).startsWith("/admin/");
      } else {
        assertThat(row.path()).as(row.id()).startsWith("/").doesNotStartWith("/store/");
        assertThat(row.path()).as(row.id()).doesNotStartWith("/admin/");
      }
    }
  }
}
