package com.kizuna.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.member.application.MemberDirectoryService.MemberProfile;
import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRank;
import com.kizuna.member.domain.MemberRankHistory;
import com.kizuna.member.domain.MemberRankHistoryRepository;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.member.domain.MemberSearchView;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class MemberDirectoryServiceTest {

  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-10-26T10:00:00+09:00");

  @Mock private MemberRepository memberRepository;
  @Mock private MemberRankHistoryRepository memberRankHistoryRepository;
  @Mock private UserRepository userRepository;

  @InjectMocks private MemberDirectoryService service;

  private record View(
      Long id,
      String memberCode,
      MemberRank rank,
      OffsetDateTime withdrawnAt,
      OffsetDateTime createdAt,
      String email,
      String displayName)
      implements MemberSearchView {
    @Override
    public Long getId() {
      return id;
    }

    @Override
    public String getMemberCode() {
      return memberCode;
    }

    @Override
    public MemberRank getRank() {
      return rank;
    }

    @Override
    public OffsetDateTime getWithdrawnAt() {
      return withdrawnAt;
    }

    @Override
    public OffsetDateTime getCreatedAt() {
      return createdAt;
    }

    @Override
    public String getEmail() {
      return email;
    }

    @Override
    public String getDisplayName() {
      return displayName;
    }
  }

  @Test
  @DisplayName("既定の有効は未退会だけの問い合わせへ行き、空の検索語は全件に当たるパターンになる")
  void activeSearchUsesActiveQueryWithMatchAllPattern() {
    Page<MemberSearchView> page =
        new PageImpl<>(
            List.of(new View(1L, "123456789012", MemberRank.GOLD, null, AT, "a@x.jp", "花子")));
    when(memberRepository.searchActive(eq("%"), any())).thenReturn(page);

    var result = service.search(MemberStatusFilter.ACTIVE, "  ", PageRequest.of(0, 20));

    assertThat(result.getContent())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.memberCode()).isEqualTo("123456789012");
              assertThat(row.rank()).isEqualTo("GOLD");
              assertThat(row.email()).isEqualTo("a@x.jp");
            });
    verify(memberRepository, never()).searchWithdrawn(any(), any());
    verify(memberRepository, never()).searchAll(any(), any());
  }

  @Test
  @DisplayName("退会済み・すべては各々の問い合わせへ行き、呼出側の sort は捨てて位置と件数だけを渡す")
  void statusSelectsQueryAndSortIsDropped() {
    when(memberRepository.searchWithdrawn(any(), any())).thenReturn(Page.empty());
    when(memberRepository.searchAll(any(), any())).thenReturn(Page.empty());
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

    service.search(
        MemberStatusFilter.WITHDRAWN, "hanako", PageRequest.of(2, 10, Sort.by("displayName")));
    service.search(MemberStatusFilter.ALL, "hanako", PageRequest.of(0, 10));

    verify(memberRepository).searchWithdrawn(eq("%hanako%"), pageable.capture());
    verify(memberRepository).searchAll(eq("%hanako%"), any());
    assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
    assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
    assertThat(pageable.getValue().getSort().isUnsorted()).isTrue();
  }

  @Test
  @DisplayName("検索語は小文字化し、LIKE の記号は '!' でエスケープする")
  void likePatternEscapesWildcards() {
    assertThat(MemberDirectoryService.likePattern("A_b%c!d")).isEqualTo("%a!_b!%c!!d%");
    assertThat(MemberDirectoryService.likePattern(null)).isEqualTo("%");
  }

  @Test
  @DisplayName("本人退会の照会は操作者を本人として本人の表示名で返す")
  void profileOfSelfWithdrawnMember() {
    Member member = member();
    member.withdraw("引っ越し", 10L, AT);
    when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
    when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, "会員 花子")));
    when(memberRankHistoryRepository.findByMemberIdOrderByIdAsc(7L))
        .thenReturn(
            List.of(
                MemberRankHistory.promoted(
                    7L, MemberRank.BRONZE, MemberRank.SILVER, 5L, null, AT)));

    MemberProfile profile = service.findProfile(7L).orElseThrow();

    assertThat(profile.withdrawnBySelf()).isTrue();
    assertThat(profile.withdrawnByName()).isEqualTo("会員 花子");
    assertThat(profile.withdrawalReason()).isEqualTo("引っ越し");
    assertThat(profile.email()).isEqualTo("member@example.com");
    assertThat(profile.rankHistory())
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.previousRank()).isEqualTo("BRONZE");
              assertThat(t.newRank()).isEqualTo("SILVER");
            });
    verify(userRepository, never()).findById(42L);
  }

  @Test
  @DisplayName("職員による退会の照会は操作者の表示名を職員の行から引く")
  void profileOfStaffWithdrawnMember() {
    Member member = member();
    member.withdraw("電話依頼", 42L, AT);
    when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
    when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, "会員 花子")));
    when(userRepository.findById(42L)).thenReturn(Optional.of(user(42L, "職員 太郎")));
    when(memberRankHistoryRepository.findByMemberIdOrderByIdAsc(7L)).thenReturn(List.of());

    MemberProfile profile = service.findProfile(7L).orElseThrow();

    assertThat(profile.withdrawnBySelf()).isFalse();
    assertThat(profile.withdrawnByName()).isEqualTo("職員 太郎");
  }

  @Test
  @DisplayName("存在しない会員は空")
  void missingMemberIsEmpty() {
    when(memberRepository.findById(99L)).thenReturn(Optional.empty());
    assertThat(service.findProfile(99L)).isEmpty();
  }

  private static Member member() {
    Member member = Member.builder().memberCode("123456789012").userId(10L).build();
    member.setId(7L);
    return member;
  }

  private static User user(long id, String displayName) {
    User user =
        User.builder()
            .email("member@example.com")
            .password("encoded")
            .displayName(displayName)
            .enabled(true)
            .userType(UserType.MEMBER)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of())
            .build();
    user.setId(id);
    return user;
  }
}
