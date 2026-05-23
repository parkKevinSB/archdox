package io.github.parkkevinsb.bloom.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring bean method as a Bloom event handler.
 *
 * <p>The method must be an instance method with exactly one parameter. That
 * parameter type becomes the Bloom event type used for subscription.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {
}
