package com.kizuna.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the annotated method should have automatic tenant filtering applied.
 *
 * <p>When this annotation is present, the system will automatically inject the current tenant's ID
 * and apply tenant-specific filtering logic to the method.
 *
 * <p><b>Usage Example:</b>
 *
 * <pre>{@code
 * @TenantScoped
 * public List<User> getUsersForTenant() {
 *     // Implementation that will be automatically filtered by tenant
 * }
 * }</pre>
 *
 * @author kanghouchao
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantScoped {}
