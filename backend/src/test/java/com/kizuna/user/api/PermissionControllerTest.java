package com.kizuna.user.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.user.api.dto.PermissionGroupResponse;
import com.kizuna.user.api.dto.PermissionResponse;
import com.kizuna.user.api.dto.PermissionTreeResponse;
import com.kizuna.user.domain.PermissionCode;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PermissionControllerTest {

  private final List<PermissionTreeResponse> body = new PermissionController().list().getBody();

  @Test
  @DisplayName("木はグループ・店舗の順で、それぞれ日本語名を伴う")
  void treesInDeclarationOrder() {
    assertThat(body).extracting(PermissionTreeResponse::tree).containsExactly("GROUP", "STORE");
    assertThat(body).extracting(PermissionTreeResponse::label).containsExactly("グループ", "店舗");
  }

  @Test
  @DisplayName("区分見出しは木ごとに宣言順で並び、権限の無い見出しは出さない")
  void groupsInDeclarationOrderWithoutEmptyOnes() {
    assertThat(groupsOf("GROUP"))
        .extracting(PermissionGroupResponse::group)
        .containsExactly("BUSINESS", "CAST", "CUSTOMER", "ORGANIZATION", "OPERATION");
    assertThat(groupsOf("STORE"))
        .extracting(PermissionGroupResponse::group)
        .containsExactly("BUSINESS", "CAST", "CUSTOMER", "OPERATION");
    body.forEach(
        tree -> tree.groups().forEach(group -> assertThat(group.permissions()).isNotEmpty()));
  }

  @Test
  @DisplayName("各見出しは、その木と見出しに属する権限だけを宣言順で全部並べる")
  void permissionsUnderOwnTreeAndGroup() {
    body.forEach(
        tree ->
            tree.groups()
                .forEach(
                    group ->
                        assertThat(codesUnder(tree.tree(), group.group()))
                            .containsExactlyElementsOf(
                                Arrays.stream(PermissionCode.values())
                                    .filter(code -> code.getTree().name().equals(tree.tree()))
                                    .filter(code -> code.getGroup().name().equals(group.group()))
                                    .map(PermissionCode::name)
                                    .toList())));
    // 同じ見出し（業務）でも木が違えば別の束に入る
    assertThat(codesUnder("GROUP", "BUSINESS"))
        .contains("ORDER_SET_VIEW")
        .doesNotContain("ORDER_MANAGE");
    assertThat(codesUnder("STORE", "BUSINESS"))
        .contains("ORDER_MANAGE")
        .doesNotContain("ORDER_SET_VIEW");
  }

  @Test
  @DisplayName("全権限がちょうど 1 回ずつ現れ、日本語名を伴う")
  void everyPermissionAppearsOnce() {
    List<PermissionResponse> all =
        body.stream()
            .flatMap(tree -> tree.groups().stream())
            .flatMap(group -> group.permissions().stream())
            .toList();

    assertThat(all).hasSize(PermissionCode.values().length);
    assertThat(all).extracting(PermissionResponse::code).doesNotHaveDuplicates();
    assertThat(all)
        .extracting(PermissionResponse::label)
        .allSatisfy(l -> assertThat(l).isNotBlank());
  }

  private List<PermissionGroupResponse> groupsOf(String tree) {
    return body.stream().filter(t -> t.tree().equals(tree)).findFirst().orElseThrow().groups();
  }

  private List<String> codesUnder(String tree, String group) {
    return groupsOf(tree).stream()
        .filter(g -> g.group().equals(group))
        .findFirst()
        .orElseThrow()
        .permissions()
        .stream()
        .map(PermissionResponse::code)
        .toList();
  }
}
