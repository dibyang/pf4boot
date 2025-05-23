package net.xdob.pf4boot.spring.boot;

import net.xdob.pf4boot.Pf4bootPluginManager;
import net.xdob.pf4boot.internal.PluginPathResourceResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.cache.Cache;
import org.springframework.web.servlet.mvc.method.PluginRequestMappingHandlerMapping;
import org.pf4j.PluginManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.servlet.PluginResourceHandlerRegistrationCustomizer;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;


@Configuration
@ConditionalOnClass({PluginManager.class, Pf4bootPluginManager.class})
@ConditionalOnProperty(prefix = Pf4bootProperties.PREFIX, value = "enabled", havingValue = "true")
public class Pf4bootMvcPatchAutoConfiguration {

  @Autowired
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  private WebProperties resourceProperties;// = new WebProperties();

  @Autowired(required = false)
  @Qualifier("sbpResourceCache")
  private Cache sbpResourceCache;


  @Bean
  @ConditionalOnMissingBean(WebMvcRegistrations.class)
  public WebMvcRegistrations mvcRegistrations() {
    return new WebMvcRegistrations() {
      @Override
      public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        return new PluginRequestMappingHandlerMapping();
      }

      @Override
      public RequestMappingHandlerAdapter getRequestMappingHandlerAdapter() {
        return null;
      }

      @Override
      public ExceptionHandlerExceptionResolver getExceptionHandlerExceptionResolver() {
        return null;
      }
    };
  }

  @Bean
  public PluginResourceHandlerRegistrationCustomizer resourceHandlerRegistrationCustomizer(PluginPathResourceResolver pluginPathResourceResolver) {
    return new PluginResourceHandlerRegistrationCustomizer(resourceProperties, sbpResourceCache, pluginPathResourceResolver);
  }

  @EventListener(AppCacheFreeEvent.class)
  public void onPluginStarted() {

  }
}