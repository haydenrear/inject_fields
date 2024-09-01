package com.hayden.inject_fields;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@Aspect
public class AspectJAutowireParameter implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Around("@annotation(com.hayden.inject_fields.AutowireParameter)")
    public Object intercept(ProceedingJoinPoint joinPoint) throws Throwable {
        if (joinPoint.getSignature() instanceof MethodSignature m) {
            int argNum = 0;
            for (var p : m.getMethod().getParameterAnnotations()) {
                for (var p1 : p) {
                    if (p1 instanceof DoAutowireParameter d) {
                        this.applicationContext.getAutowireCapableBeanFactory().autowireBean(joinPoint.getArgs()[argNum]);
                    }
                }
                argNum += 1;
            }
        }
        // Do something before the method call
        Object result = joinPoint.proceed();
        // Do something after the method call
        return result;
    }

    @Override
    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
