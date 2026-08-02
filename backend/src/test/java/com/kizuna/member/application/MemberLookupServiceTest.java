package com.kizuna.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberLookupServiceTest {

  @Mock private MemberRepository memberRepository;

  @InjectMocks private MemberLookupService service;

  @Test
  @DisplayName("会員コードで会員 ID とコードだけを引けること（プロフィールは返さない）")
  void findByMemberCodeReturnsMinimalLookup() {
    Member member = Member.builder().memberCode("123456789012").platformUserId(10L).build();
    Mockito.when(memberRepository.findByMemberCode("123456789012")).thenReturn(Optional.of(member));

    Optional<MemberLookup> lookup = service.findByMemberCode("123456789012");

    assertThat(lookup).isPresent();
    assertThat(lookup.get().memberCode()).isEqualTo("123456789012");
  }

  @Test
  @DisplayName("存在しない会員コードでは空を返すこと")
  void findByMemberCodeReturnsEmptyWhenUnknown() {
    Mockito.when(memberRepository.findByMemberCode("999999999999")).thenReturn(Optional.empty());

    assertThat(service.findByMemberCode("999999999999")).isEmpty();
  }
}
