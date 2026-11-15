package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CreatedStoreGrantTest {

  private final UserRepository users = mock(UserRepository.class);
  private final BusinessAudit audit = mock(BusinessAudit.class);
  private final CreatedStoreGrant grant = new CreatedStoreGrant(users, audit);

  @Test
  void addsTheStoreToTheLockedCreatorAndAuditsAsTheCreator() {
    var creator =
        User.builder()
            .email("creator@kizuna.test")
            .password("hash")
            .displayName("作成者")
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(Set.of(1L))
            .storeIds(Set.of(3L))
            .build();
    creator.setId(42L);
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(creator));

    grant.grantToCreator(42L, 7L);

    assertThat(creator.getStoreIds()).containsExactlyInAnyOrder(3L, 7L);
    verify(users).save(creator);
    verify(audit)
        .recordById(
            eq(42L),
            eq(null),
            eq("STAFF_GRANTS_CHANGED"),
            eq("USER_GRANTS"),
            eq("42"),
            eq(null),
            eq(null),
            eq(Map.of("user_type", "STAFF", "enabled", "true", "role_ids", "1", "store_ids", "3")),
            eq(
                Map.of(
                    "user_type", "STAFF", "enabled", "true", "role_ids", "1", "store_ids", "3,7")));
  }

  @Test
  void missingCreatorIsAStaleSessionAndRecordsNothing() {
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> grant.grantToCreator(42L, 7L))
        .isInstanceOf(StaleSessionException.class);

    verify(users, never()).save(any());
    verifyNoInteractions(audit);
  }
}
