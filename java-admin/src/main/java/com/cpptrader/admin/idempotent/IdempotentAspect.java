package com.cpptrader.admin.idempotent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(com.cpptrader.admin.idempotent.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);

        String key = buildKey(idempotent, joinPoint.getArgs());
        String redisKey = idempotent.prefix() + ":" + key;

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", idempotent.expireSeconds(), TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(acquired)) {
            log.warn("Idempotent check failed, duplicate request: key={}", redisKey);
            throw new RuntimeException("Duplicate request detected");
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            redisTemplate.delete(redisKey);
            throw t;
        }
    }

    private String buildKey(Idempotent idempotent, Object[] args) {
        if (!idempotent.key().isEmpty()) {
            return idempotent.key();
        }
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg != null) {
                sb.append(arg.hashCode()).append(":");
            }
        }
        return sb.toString();
    }
}
