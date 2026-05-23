package io.github.parkkevinsb.bloom.spring;

import io.github.parkkevinsb.bloom.EventBus;
import io.github.parkkevinsb.bloom.EventHandler;
import io.github.parkkevinsb.bloom.Subscription;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers Spring bean methods annotated with {@link Subscribe} with a Bloom
 * {@link EventBus}, and closes those subscriptions when the bean is destroyed.
 */
public final class BloomBeanPostProcessor
        implements BeanPostProcessor, DestructionAwareBeanPostProcessor, PriorityOrdered {

    private final EventBus eventBus;
    private final Map<String, List<Subscription>> subscriptionsByBeanName = new ConcurrentHashMap<>();

    public BloomBeanPostProcessor(EventBus eventBus) {
        if (eventBus == null) {
            throw new IllegalArgumentException("eventBus must not be null");
        }
        this.eventBus = eventBus;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean == null) {
            return null;
        }

        List<Method> methods = findSubscribeMethods(bean.getClass());
        if (methods.isEmpty()) {
            return bean;
        }

        List<Subscription> subscriptions = new ArrayList<>(methods.size());
        try {
            for (Method method : methods) {
                subscriptions.add(subscribe(bean, beanName, method));
            }
        } catch (RuntimeException e) {
            closeAll(subscriptions);
            throw e;
        }
        subscriptionsByBeanName.put(beanName, Collections.unmodifiableList(subscriptions));
        return bean;
    }

    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
        List<Subscription> subscriptions = subscriptionsByBeanName.remove(beanName);
        if (subscriptions != null) {
            closeAll(subscriptions);
        }
    }

    @Override
    public boolean requiresDestruction(Object bean) {
        return true;
    }

    private List<Method> findSubscribeMethods(Class<?> beanClass) {
        List<Method> methods = new ArrayList<>();
        ReflectionUtils.doWithMethods(beanClass, method -> {
            validate(method);
            ReflectionUtils.makeAccessible(method);
            methods.add(method);
        }, method -> method.isAnnotationPresent(Subscribe.class)
                && !method.isBridge()
                && !method.isSynthetic());
        return methods;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Subscription subscribe(Object bean, String beanName, Method method) {
        Class<?> eventType = method.getParameterTypes()[0];
        EventHandler handler = event -> invoke(bean, beanName, method, event);
        return eventBus.subscribe(eventType, handler);
    }

    private void invoke(Object bean, String beanName, Method method, Object event) {
        try {
            method.invoke(bean, event);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                    "Cannot access @Subscribe method " + describe(beanName, method), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(
                    "@Subscribe method threw checked exception " + describe(beanName, method), cause);
        }
    }

    private void validate(Method method) {
        if (Modifier.isStatic(method.getModifiers())) {
            throw new BeanCreationException(
                    "@Subscribe method must not be static: " + method.toGenericString());
        }
        if (method.getParameterTypes().length != 1) {
            throw new BeanCreationException(
                    "@Subscribe method must declare exactly one parameter: " + method.toGenericString());
        }
        if (method.getReturnType() != Void.TYPE) {
            throw new BeanCreationException(
                    "@Subscribe method must return void: " + method.toGenericString());
        }
    }

    private String describe(String beanName, Method method) {
        return "'" + beanName + "'." + method.getName();
    }

    private void closeAll(List<Subscription> subscriptions) {
        for (Subscription subscription : subscriptions) {
            try {
                subscription.close();
            } catch (Throwable ignored) {
                // Subscription.close is best-effort and should not derail bean destruction.
            }
        }
    }
}
