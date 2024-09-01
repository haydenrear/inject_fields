package com.hayden.inject_fields;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Scope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AutowireParameter {

    enum AUTO {
        PRM(null);

        private final Object o;

        public <T> T INJECT_ME() {
            return (T) o;
        }

        AUTO(Object o) {
            this.o = o;
        }
    }

    Scope scope() default @Scope(DefaultListableBeanFactory.SCOPE_PROTOTYPE);

}
