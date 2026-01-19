package com.cms.service.central.tenant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

import com.cms.exception.ServiceException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class TenantRegistrationServiceImplTest {

  @Mock StringRedisTemplate redisTemplate;

  @Mock ValueOperations<String, String> valueOps;

  @InjectMocks TenantRegistrationServiceImpl svc;

  @BeforeEach
  void setUp() {
    // use lenient in case some tests don't interact with opsForValue directly
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
  }

  @Test
  void createToken_storesValueAndReturnsToken() {
    doNothing().when(valueOps).set(any(String.class), any(String.class), any(Duration.class));

    String token = svc.createToken(123L);

    assertNotNull(token);
    // token should be URL-safe base64 without padding -> no '='
    assertFalse(token.contains("="));

    verify(redisTemplate).opsForValue();
    verify(valueOps).set(startsWith("tenant:registration:"), eq("123"), any(Duration.class));
  }

  @Test
  void validate_returnsTenantId_whenPresentAndValid() {
    String token = "abc";
    when(valueOps.get("tenant:registration:" + token)).thenReturn("456");

    Long id = svc.validate(token);

    assertEquals(456L, id);
  }

  @Test
  void validate_throws_whenMissing() {
    String token = "missing";
    when(valueOps.get("tenant:registration:" + token)).thenReturn(null);

    assertThrows(ServiceException.class, () -> svc.validate(token));
  }

  @Test
  void validate_throws_whenNotNumber() {
    String token = "bad";
    when(valueOps.get("tenant:registration:" + token)).thenReturn("not-a-number");

    assertThrows(RuntimeException.class, () -> svc.validate(token));
  }

  @Test
  void consume_deletesKey() {
    String token = "todel";

    svc.consume(token);

    verify(redisTemplate).delete("tenant:registration:" + token);
  }
}
