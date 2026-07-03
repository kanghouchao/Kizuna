package com.kizuna.tenant.domain.event;

/** テナント作成時に発行されるドメインイベント。イベント発行レジストリで JSON 直列化されるため、集約そのものではなく必要な属性のみを持つ。 */
public record TenantCreatedEvent(
    Long tenantId, String name, String domain, String email, String token) {}
