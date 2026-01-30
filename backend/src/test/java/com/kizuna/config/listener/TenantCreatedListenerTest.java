package com.kizuna.config.listener;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.config.AppProperties;
import com.kizuna.config.listener.event.TenantCreatedEvent;
import com.kizuna.model.entity.central.tenant.Tenant;
import com.kizuna.service.central.tenant.TenantRegistrationService;
import com.kizuna.service.mail.MailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantCreatedListenerTest {

  @Mock private MailService mailService;
  @Mock private TenantRegistrationService registrationService;
  @Mock private AppProperties appProperties;

  @InjectMocks private TenantCreatedListener listener;

  @Test
  void onTenantCreated_sendsEmail() {
    Tenant tenant = new Tenant();
    tenant.setId(1L);
    tenant.setDomain("test.com");
    tenant.setEmail("owner@test.com");
    tenant.setName("Test Tenant");

    when(registrationService.createToken(1L)).thenReturn("token123");
    when(appProperties.getScheme()).thenReturn("https");

    listener.onTenantCreated(new TenantCreatedEvent(tenant));

    verify(mailService).send(eq("owner@test.com"), anyString(), anyString());
  }

  @Test
  void onTenantCreated_skipsIfNoEmail() {
    Tenant tenant = new Tenant();
    tenant.setId(1L);
    tenant.setDomain("test.com");

    listener.onTenantCreated(new TenantCreatedEvent(tenant));

    verifyNoInteractions(mailService);
  }

  @Test
  void onTenantCreated_handlesMailException() {
    Tenant tenant = new Tenant();
    tenant.setId(1L);
    tenant.setDomain("test.com");
    tenant.setEmail("error@test.com");

    when(registrationService.createToken(anyLong())).thenReturn("token");
    when(appProperties.getScheme()).thenReturn("http");
    doThrow(new RuntimeException("SMTP Down"))
        .when(mailService)
        .send(anyString(), anyString(), anyString());

    listener.onTenantCreated(new TenantCreatedEvent(tenant));
  }
}
