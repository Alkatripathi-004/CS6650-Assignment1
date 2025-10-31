package com.chat.cs6650assignment2.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * This configuration class is responsible for setting up Micrometer features.
 * The @EnableAspectJAutoProxy is crucial for allowing Spring to apply AOP,
 * which is how the @Timed annotation works.
 */
@Configuration
@EnableAspectJAutoProxy
public class MicrometerConfig {

    /**
     * This bean is the magic that finds all methods annotated with @Timed,
     * and automatically wraps them with timing logic.
     * @param registry The MeterRegistry, which Spring Boot provides automatically.
     * @return A TimedAspect bean.
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}