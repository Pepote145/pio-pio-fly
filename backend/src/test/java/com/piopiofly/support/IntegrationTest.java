package com.piopiofly.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Test con la aplicación completa sobre PostgreSQL embebido y sin tareas programadas. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import(EmbeddedPostgresTestConfig.class)
@ActiveProfiles("test")
public @interface IntegrationTest {
}
