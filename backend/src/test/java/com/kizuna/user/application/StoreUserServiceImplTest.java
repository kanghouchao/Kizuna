package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.api.dto.StoreUserMeResponse;
import com.kizuna.user.domain.StoreUser;
import com.kizuna.user.domain.StoreUserRepository;
import com.kizuna.user.domain.TenantRole;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreUserServiceImplTest {

  @Mock private StoreUserRepository userRepository;

  @InjectMocks private StoreUserServiceImpl service;

  private StoreUser user(String nickname) {
    StoreUser user = new StoreUser();
    user.setId("u1");
    user.setNickname(nickname);
    user.setEmail("staff@store1.kizuna.com");
    TenantRole role = new TenantRole();
    role.setName("ADMIN");
    user.setRoles(Set.of(role));
    return user;
  }

  @Test
  void me_returnsCurrentUser() {
    when(userRepository.findByEmail("staff@store1.kizuna.com"))
        .thenReturn(Optional.of(user("スタッフA")));

    StoreUserMeResponse res = service.me("staff@store1.kizuna.com");

    assertThat(res.getNickname()).isEqualTo("スタッフA");
    assertThat(res.getEmail()).isEqualTo("staff@store1.kizuna.com");
    assertThat(res.getRole()).isEqualTo("ADMIN");
  }

  @Test
  void me_userNotFound_throws() {
    when(userRepository.findByEmail("missing@x.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.me("missing@x.com"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("ユーザーが見つかりません");
  }

  @Test
  void updateProfile_renamesAndSaves() {
    StoreUser u = user("旧名");
    when(userRepository.findByEmail("staff@store1.kizuna.com")).thenReturn(Optional.of(u));
    when(userRepository.save(u)).thenReturn(u);

    StoreUserMeResponse res = service.updateProfile("staff@store1.kizuna.com", "新名");

    assertThat(u.getNickname()).isEqualTo("新名");
    assertThat(res.getNickname()).isEqualTo("新名");
  }
}
