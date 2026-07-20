package com.kizuna.shared.storescope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the annotated method should have automatic store filtering applied.
 *
 * <p>When this annotation is present, the system will automatically inject the current store's ID
 * and apply store-specific filtering logic to the method.
 *
 * <p><b>Usage Example:</b>
 *
 * <pre>{@code
 * @StoreScoped
 * public List<User> getUsersForStore() {
 *     // Implementation that will be automatically filtered by store
 * }
 * }</pre>
 *
 * @author kanghouchao
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StoreScoped {}
