package com.kizuna.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class BaseEntityTest {

  @Test
  void testCentralBaseEntity() {
    com.kizuna.model.entity.central.BaseEntity entity =
        new com.kizuna.model.entity.central.BaseEntity() {};
    OffsetDateTime now = OffsetDateTime.now();
    entity.setId(1L);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);

    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getCreatedAt()).isEqualTo(now);
    assertThat(entity.getUpdatedAt()).isEqualTo(now);
  }

  @Test
  void testTenantBaseEntity() {
    com.kizuna.model.entity.tenant.BaseEntity entity =
        new com.kizuna.model.entity.tenant.BaseEntity() {};
    OffsetDateTime now = OffsetDateTime.now();
    entity.setId("id");
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);

    assertThat(entity.getId()).isEqualTo("id");
    assertThat(entity.getCreatedAt()).isEqualTo(now);
    assertThat(entity.getUpdatedAt()).isEqualTo(now);
  }
}
