package net.xdob.pf4boot.spring.boot;


import net.xdob.pf4boot.internal.*;
import net.xdob.pf4boot.*;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import org.pf4j.PluginStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

@Configuration
@ConditionalOnClass({PluginManager.class, Pf4bootPluginManagerImpl.class})
@ConditionalOnProperty(prefix = Pf4bootProperties.PREFIX, value = "enabled", havingValue = "true")
@EnableConfigurationProperties({Pf4bootProperties.class, Pf4bootPluginProperties.class})
@Import({DefaultPluginEventListener.class, MainAppStartedListener.class})
public class Pf4bootAutoConfiguration {
  static final Logger log = LoggerFactory.getLogger(Pf4bootAutoConfiguration.class);

  @Bean
  @ConditionalOnMissingBean
  public Pf4bootEventBus eventBus(){
    return new Pf4bootEventBusImpl();
  }


  @Bean
  @ConditionalOnMissingBean(PluginStateListener.class)
  public PluginStateListener pluginStateListener() {
    return event -> {
      PluginDescriptor descriptor = event.getPlugin().getDescriptor();
      if (log.isDebugEnabled()) {
        log.debug("Plugin [{}（{}）]({}) {}", descriptor.getPluginId(),
            descriptor.getVersion(), descriptor.getPluginDescription(),
            event.getPluginState().toString());
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean(PluginManagerController.class)
  @ConditionalOnProperty(name = "spring.pf4boot.controller.base-path")
  public PluginManagerController pluginManagerController(Pf4bootPluginManager pluginManager) {
    return new PluginManagerController(pluginManager);
  }

  @Bean
  @ConditionalOnMissingBean
  public PluginResourceResolver pluginResourceResolver() {
    return new PluginResourceResolver();
  }

  @Bean
  @ConditionalOnMissingBean(DefaultPf4bootPluginSupport.class)
  public DefaultPf4bootPluginSupport defaultPf4bootPluginSupport(){
    return new DefaultPf4bootPluginSupport();
  }

  @Bean
  @ConditionalOnClass({WebPf4BootPluginSupport.class})
  public WebPf4BootPluginSupport webPf4BootPluginSupport(){
    return new WebPf4BootPluginSupport();
  }

  @Bean
  @ConditionalOnMissingBean
  public Pf4bootPluginManager pluginManager(Pf4bootProperties properties, Pf4bootEventBus eventBus, List<Pf4bootPluginSupport> pluginSupports) {
    // Setup RuntimeMode
    System.setProperty("pf4j.mode", properties.getRuntimeMode().toString());

    // Setup Plugin folder
    String pluginsRoot = StringUtils.hasText(properties.getPluginsRoot()) ? properties.getPluginsRoot() : "plugins";

    System.setProperty("pf4j.pluginsDir", pluginsRoot);

    Pf4bootPluginManager pluginManager = new Pf4bootPluginManagerImpl(properties, eventBus, pluginSupports, new File(pluginsRoot).toPath());

    pluginManager.setProfiles(properties.getPluginProfiles());
    pluginManager.presetProperties(flatProperties(properties.getPluginProperties()));
    pluginManager.setExactVersionAllowed(properties.isExactVersionAllowed());
    pluginManager.setSystemVersion(properties.getSystemVersion());

    return pluginManager;
  }

  private Map<String, Object> flatProperties(Map<String, Object> propertiesMap) {
    Stack<String> pathStack = new Stack<>();
    Map<String, Object> flatMap = new HashMap<>();
    propertiesMap.entrySet().forEach(mapEntry -> {
      recurse(mapEntry, entry -> {
        pathStack.push(entry.getKey());
        if (entry.getValue() instanceof Map) return;
        flatMap.put(String.join(".", pathStack), entry.getValue());

      }, entry -> {
        pathStack.pop();
      });
    });
    return flatMap;
  }

  private void recurse(Map.Entry<String, Object> entry,
                       Consumer<Map.Entry<String, Object>> preConsumer,
                       Consumer<Map.Entry<String, Object>> postConsumer) {
    preConsumer.accept(entry);

    if (entry.getValue() instanceof Map) {
      Map<String, Object> entryMap = (Map<String, Object>) entry.getValue();
      for (Map.Entry<String, Object> subEntry : entryMap.entrySet()) {
        recurse(subEntry, preConsumer, postConsumer);
      }
    }

    postConsumer.accept(entry);
  }

}