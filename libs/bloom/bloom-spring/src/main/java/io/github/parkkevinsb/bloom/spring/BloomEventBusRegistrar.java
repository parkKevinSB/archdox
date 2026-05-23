package io.github.parkkevinsb.bloom.spring;

import io.github.parkkevinsb.bloom.EventBus;
import io.github.parkkevinsb.bloom.LocalEventBus;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.ClassUtils;

/**
 * Registers Bloom's default EventBus only when the application did not define
 * any {@link EventBus} bean of its own.
 */
public final class BloomEventBusRegistrar implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    static final String DEFAULT_EVENT_BUS_BEAN_NAME = "bloomEventBus";

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (containsEventBusDefinition(registry)) {
            return;
        }
        BeanDefinition beanDefinition =
                BeanDefinitionBuilder.rootBeanDefinition(LocalEventBus.class, "create")
                        .getBeanDefinition();
        registry.registerBeanDefinition(DEFAULT_EVENT_BUS_BEAN_NAME, beanDefinition);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op
    }

    private boolean containsEventBusDefinition(BeanDefinitionRegistry registry) {
        for (String name : registry.getBeanDefinitionNames()) {
            if (DEFAULT_EVENT_BUS_BEAN_NAME.equals(name)) {
                continue;
            }
            BeanDefinition beanDefinition = registry.getBeanDefinition(name);
            if (isEventBusClass(beanDefinition.getBeanClassName())) {
                return true;
            }
            if (beanDefinition instanceof AnnotatedBeanDefinition) {
                MethodMetadata methodMetadata =
                        ((AnnotatedBeanDefinition) beanDefinition).getFactoryMethodMetadata();
                if (methodMetadata != null && isEventBusClass(methodMetadata.getReturnTypeName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isEventBusClass(String className) {
        if (className == null) {
            return false;
        }
        try {
            Class<?> type = ClassUtils.forName(className, getClass().getClassLoader());
            return EventBus.class.isAssignableFrom(type);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
