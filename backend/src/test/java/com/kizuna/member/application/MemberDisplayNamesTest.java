package com.kizuna.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.member.domain.MemberDisplayNameView;
import com.kizuna.member.domain.MemberRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemberDisplayNamesTest {
  private final MemberRepository members = mock(MemberRepository.class);
  private final MemberDisplayNames names = new MemberDisplayNames(members);

  @Test
  void mapsMemberIdsToDisplayNamesAndSkipsTheQueryForNoMembers() {
    assertThat(names.of(List.of())).isEmpty();
    verifyNoInteractions(members);
    when(members.findDisplayNames(List.of(1L, 2L)))
        .thenReturn(List.of(view(1L, "会員一"), view(2L, "会員二")));
    assertThat(names.of(List.of(1L, 2L))).isEqualTo(Map.of(1L, "会員一", 2L, "会員二"));
  }

  private static MemberDisplayNameView view(Long id, String name) {
    return new MemberDisplayNameView() {
      public Long getMemberId() {
        return id;
      }

      public String getDisplayName() {
        return name;
      }
    };
  }
}
