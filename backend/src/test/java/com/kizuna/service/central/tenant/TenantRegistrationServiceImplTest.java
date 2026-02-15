package com.kizuna.service.central.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.exception.ServiceException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TenantRegistrationServiceImplTest {

  @Mock private StringRedisTemplate redisTemplate;
  @InjectMocks private TenantRegistrationServiceImpl registrationService;

  @Test
  @SuppressWarnings("unchecked")
  void createToken_savesInRedis() {
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOps);

    String token = registrationService.createToken(1L);

    assertThat(token).isNotNull();
    verify(valueOps).set(eq("tenant:registration:" + token), eq("1"), eq(Duration.ofDays(7)));
  }

  @Test
  @SuppressWarnings("unchecked")
  void validate_returnsFromRedis() {
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.get("tenant:registration:token")).thenReturn("123");

    Long id = registrationService.validate("token");
    assertThat(id).isEqualTo(123L);
    verify(valueOps).get("tenant:registration:token");
  }

  @Test
  @SuppressWarnings("unchecked")
  void validate_throwsWhenTokenMissing() {
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.get(anyString())).thenReturn(null);

    assertThatThrownBy(() -> registrationService.validate("missing-token"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("Invalid or expired token");
  }

  @Test
  @SuppressWarnings("unchecked")
  void validate_throwsWhenValueIsNotNumber() {
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.get(anyString())).thenReturn("not-a-number");

    assertThatThrownBy(() -> registrationService.validate("bad-token"))
        .isInstanceOf(ServiceException.class)
        .hasCauseInstanceOf(NumberFormatException.class);
  }

  @Test
  void consume_deletesTokenFromRedis() {
    registrationService.consume("consumable-token");

    verify(redisTemplate).delete("tenant:registration:consumable-token");
  }
}
