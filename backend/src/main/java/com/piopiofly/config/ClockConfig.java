package com.piopiofly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /** Hora de Canarias: "hoy" es el día de quien sale desde Gran Canaria. */
    public static final ZoneId CANARY_ZONE = ZoneId.of("Atlantic/Canary");

    @Bean
    Clock clock() {
        return Clock.system(CANARY_ZONE);
    }
}
