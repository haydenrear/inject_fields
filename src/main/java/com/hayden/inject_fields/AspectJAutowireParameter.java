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

import java.util.Optional;

@Component
@Aspect
public class AspectJAutowireParameter implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Around("@annotation(autowireParameter)")
    public Object intercept(ProceedingJoinPoint joinPoint,
                            AutowireParameter autowireParameter) throws Throwable {
        if (joinPoint.getSignature() instanceof MethodSignature m) {
            int argNum = 0;
            for (var p : m.getMethod().getParameters()) {
                Class<?> type = p.getType();
                boolean isAutowireParam = type.getAnnotation(AutowireParameter.class) != null;
                if (isAutowireParam) {
                    int finalArgNum = argNum;
                    Optional.ofNullable(joinPoint.getArgs()[argNum])
                            .ifPresentOrElse(
                                    o -> applicationContext.getAutowireCapableBeanFactory().autowireBean(o),
                                    () -> joinPoint.getArgs()[finalArgNum] = applicationContext.getBean(type));
                }
                argNum += 1;
            }
        }

        return joinPoint.proceed(joinPoint.getArgs());
    }

    @Override
    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
