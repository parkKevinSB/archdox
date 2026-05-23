package io.github.parkkevinsb.bloom.spring;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables Bloom's Spring integration.
 *
 * <p>Imports a small configuration that provides a default Bloom
 * {@link io.github.parkkevinsb.bloom.EventBus} when none exists and registers
 * the {@link BloomBeanPostProcessor} that wires {@link Subscribe} methods.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(BloomConfiguration.class)
public @interface EnableBloom {
}
