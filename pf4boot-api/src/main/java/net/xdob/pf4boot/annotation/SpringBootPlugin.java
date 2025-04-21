package net.xdob.pf4boot.annotation;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Pf4bootPlugin
 *
 * @author yangzj
 * @version 1.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootConfiguration
@ConfigurationPropertiesScan
@EnableAutoConfiguration
@ComponentScan(
    excludeFilters = {@ComponentScan.Filter(
        type = FilterType.CUSTOM,
        classes = {TypeExcludeFilter.class}
    ), @ComponentScan.Filter(
        type = FilterType.CUSTOM,
        classes = {AutoConfigurationExcludeFilter.class}
    )}
)
public @interface SpringBootPlugin {

  @AliasFor(annotation = EnableAutoConfiguration.class)
  Class<?>[] exclude() default {};

  @AliasFor(annotation = EnableAutoConfiguration.class)
  String[] excludeName() default {};


  @AliasFor(annotation = ComponentScan.class)
  String[] basePackages() default {};


  @AliasFor(annotation = ComponentScan.class)
  Class<?>[] basePackageClasses() default {};

  @AliasFor(annotation = ComponentScan.class)
  ComponentScan.Filter[] includeFilters() default {};

  @AliasFor(annotation = ComponentScan.class)
  ComponentScan.Filter[] excludeFilters() default {};

  /**
   * Specify whether {@link Bean @Bean} methods should get proxied in order to enforce
   * bean lifecycle behavior, e.g. to return shared singleton bean instances even in
   * case of direct {@code @Bean} method calls in user code. This feature requires
   * method interception, implemented through a runtime-generated CGLIB subclass which
   * comes with limitations such as the configuration class and its methods not being
   * allowed to declare {@code final}.
   * <p>
   * The default is {@code true}, allowing for 'inter-bean references' within the
   * configuration class as well as for external calls to this configuration's
   * {@code @Bean} methods, e.g. from another configuration class. If this is not needed
   * since each of this particular configuration's {@code @Bean} methods is
   * self-contained and designed as a plain factory method for container use, switch
   * this flag to {@code false} in order to avoid CGLIB subclass processing.
   * <p>
   * Turning off bean method interception effectively processes {@code @Bean} methods
   * individually like when declared on non-{@code @Configuration} classes, a.k.a.
   * "@Bean Lite Mode" (see {@link Bean @Bean's javadoc}). It is therefore behaviorally
   * equivalent to removing the {@code @Configuration} stereotype.
   * @since 2.2
   * @return whether to proxy {@code @Bean} methods
   */
  @AliasFor(annotation = Configuration.class)
  boolean proxyBeanMethods() default true;
}
