package com.kizuna.controller.central;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.repository.central.CentralUserRepository;
import com.kizuna.service.central.auth.CentralAuthService;
import com.kizuna.service.central.menu.CentralMenuService;
import com.kizuna.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CentralAuthControllerLogoutTest {

  private MockMvc mockMvc;

  @Mock private CentralAuthService authService;
  @Mock private CentralUserRepository userRepository;
  @Mock private CentralMenuService menuService;
  @Mock private JwtUtil jwtUtil;
  @Mock private RedisTemplate<String, Object> redisTemplate;

  @InjectMocks private CentralAuthController controller;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  @SuppressWarnings("unchecked")
  void logout_blacklistsToken() throws Exception {
    String token = "valid-token";
    Claims claims = mock(Claims.class);
    Date exp = new Date(System.currentTimeMillis() + 10000);
    when(claims.getExpiration()).thenReturn(exp);
    when(jwtUtil.getClaims(token)).thenReturn(claims);

    ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOps);

    mockMvc
        .perform(post("/central/logout").header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    verify(valueOps).set(contains(token), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
  }
}
