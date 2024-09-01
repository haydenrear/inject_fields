package com.hayden.inject_fields;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Scope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.SOURCE)
public @interface AutowireBean {

    Class<?>[] fields() default {};

    Scope scope() default @Scope(DefaultListableBeanFactory.SCOPE_PROTOTYPE);

}
