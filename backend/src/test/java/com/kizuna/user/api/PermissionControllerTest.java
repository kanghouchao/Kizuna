package com.kizuna.user.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.user.api.dto.PermissionGroupResponse;
import com.kizuna.user.api.dto.PermissionResponse;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionGroup;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PermissionControllerTest {

  private final List<PermissionGroupResponse> body = new PermissionController().list().getBody();

  @Test
  @DisplayName("組は PermissionGroup の宣言順で返す")
  void groupsFollowEnumOrder() {
    assertThat(body)
        .extracting(PermissionGroupResponse::group)
        .containsExactlyElementsOf(
            Arrays.stream(PermissionGroup.values()).map(PermissionGroup::name).toList());
    assertThat(body)
        .extracting(PermissionGroupResponse::label)
        .containsExactly("業務", "キャスト", "顧客", "組織", "運用");
  }

  @Test
  @DisplayName("全権限がちょうど 1 回ずつ現れ、組内は code 順で日本語名を伴う")
  void everyPermissionAppearsOnceSortedWithinGroup() {
    List<PermissionResponse> all = body.stream().flatMap(g -> g.permissions().stream()).toList();

    assertThat(all).hasSize(PermissionCode.values().length);
    assertThat(all).extracting(PermissionResponse::code).doesNotHaveDuplicates();
    assertThat(all)
        .extracting(PermissionResponse::label)
        .allSatisfy(l -> assertThat(l).isNotBlank());
    body.forEach(g -> assertThat(g.permissions()).extracting(PermissionResponse::code).isSorted());
  }
}
