package com.kizuna.service.central.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
  }

  @Test
  @SuppressWarnings("unchecked")
  void validate_returnsFromRedis() {
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.get(anyString())).thenReturn("123");

    Long id = registrationService.validate("token");
    assertThat(id).isEqualTo(123L);
  }
}
