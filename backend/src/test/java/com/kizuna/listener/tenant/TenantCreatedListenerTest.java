package com.kizuna.listener.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.kizuna.config.AppProperties;
import com.kizuna.config.listener.TenantCreatedListener;
import com.kizuna.config.listener.event.TenantCreatedEvent;
import com.kizuna.model.entity.central.tenant.Tenant;
import com.kizuna.service.central.tenant.TenantRegistrationService;
import com.kizuna.service.mail.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantCreatedListenerTest {

  @Mock MailService mailService;

  @Mock TenantRegistrationService registrationService;

  @Mock AppProperties appProperties;

  @InjectMocks TenantCreatedListener listener;

  @BeforeEach
  void setUp() {
    lenient().when(appProperties.getScheme()).thenReturn("http");
  }

  @Test
  void onTenantCreated_skipsWhenNoEmail() {
    Tenant t = new Tenant();
    t.setId(10L);
    t.setName("Acme");
    t.setEmail(null);

    listener.onTenantCreated(new TenantCreatedEvent(t));

    verifyNoInteractions(registrationService);
    verifyNoInteractions(mailService);
  }

  @Test
  void onTenantCreated_sendsEmail_whenEmailPresent() {
    Tenant t = new Tenant();
    t.setId(20L);
    t.setName("Acme");
    t.setDomain("acme.example");
    t.setEmail("owner@example.com");
    when(registrationService.createToken(20L)).thenReturn("tok123");

    listener.onTenantCreated(new TenantCreatedEvent(t));

    verify(registrationService).createToken(20L);
    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    verify(mailService).send(eq("owner@example.com"), anyString(), bodyCaptor.capture());

    assertThat(bodyCaptor.getValue())
        .contains("http://acme.example/register?token=tok123")
        .contains("Acme");
  }

  @Test
  void onTenantCreated_skipsWhenNoDomain() {
    Tenant tenant = new Tenant();
    tenant.setId(21L);
    tenant.setName("NoDomain");
    tenant.setEmail("owner@example.com");
    tenant.setDomain("   ");

    listener.onTenantCreated(new TenantCreatedEvent(tenant));

    verifyNoInteractions(registrationService);
    verifyNoInteractions(mailService);
  }

  @Test
  void onTenantCreated_handlesMailException() {
    Tenant t = new Tenant();
    t.setId(30L);
    t.setName("Acme");
    t.setDomain("acme.example");
    t.setEmail("owner2@example.com");
    when(registrationService.createToken(30L)).thenReturn("tok456");
    doThrow(new RuntimeException("smtp down"))
        .when(mailService)
        .send(anyString(), anyString(), anyString());

    // should not throw
    listener.onTenantCreated(new TenantCreatedEvent(t));

    verify(registrationService).createToken(30L);
    verify(mailService).send(eq("owner2@example.com"), anyString(), anyString());
  }
}
