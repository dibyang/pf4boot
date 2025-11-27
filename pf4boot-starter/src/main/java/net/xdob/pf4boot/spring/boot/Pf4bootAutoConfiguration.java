package net.xdob.pf4boot.spring.boot;


import net.xdob.pf4boot.internal.*;
import net.xdob.pf4boot.*;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import org.pf4j.PluginStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

@Configuration
@ConditionalOnClass({PluginManager.class, Pf4bootPluginManagerImpl.class})
@ConditionalOnProperty(prefix = Pf4bootProperties.PREFIX, value = "enabled", havingValue = "true")
@EnableConfigurationProperties({Pf4bootProperties.class, Pf4bootPluginProperties.class})
public class Pf4bootAutoConfiguration {
  static final Logger LOG = LoggerFactory.getLogger(Pf4bootAutoConfiguration.class);
  public static final String PF4J_MODE = "pf4j.mode";
  public static final String PF4J_PLUGINS_DIR = "pf4j.pluginsDir";

  @Bean
  @ConditionalOnMissingBean(DefaultPluginEventListener.class)
  public DefaultPluginEventListener defaultPluginEventListener(){
    return new DefaultPluginEventListener();
  }

  @Bean
  @ConditionalOnMissingBean(MainAppStartedListener.class)
  public MainAppStartedListener mainAppStartedListener(){
    return new MainAppStartedListener();
  }


  @Bean
  @ConditionalOnMissingBean(PluginStateListener.class)
  public PluginStateListener pluginStateListener() {
    return event -> {
      PluginDescriptor descriptor = event.getPlugin().getDescriptor();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Plugin [{}（{}）]({}) {}", descriptor.getPluginId(),
            descriptor.getVersion(), descriptor.getPluginDescription(),
            event.getPluginState().toString());
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean(PluginManagerController.class)
  @ConditionalOnProperty(prefix = Pf4bootProperties.PREFIX, value = "pluginAdminEnabled", havingValue = "true", matchIfMissing = true)
  public PluginManagerController pluginManagerController(Pf4bootPluginManager pluginManager) {
    return new PluginManagerController(pluginManager);
  }

  @Bean
  @ConditionalOnMissingBean
  public PluginPathResourceResolver pluginResourceResolver(PluginManager pluginManager) {
    return new PluginPathResourceResolver(pluginManager);
  }

  @Bean
  @ConditionalOnMissingBean(DefaultShareBeanMgr.class)
  public DefaultShareBeanMgr defaultPf4bootPluginSupport(){
    return new DefaultShareBeanMgr();
  }


  @Bean
  @ConditionalOnClass({WebPf4BootPluginSupport.class})
  public WebPf4BootPluginSupport webPf4BootPluginSupport(){
    return new WebPf4BootPluginSupport();
  }

  @Bean
  @Lazy
  @ConditionalOnMissingBean
  public Pf4bootPluginManager pluginManager(ApplicationContext applicationContext,  Pf4bootProperties properties,
																						ObjectProvider<Pf4bootPluginSupport> pluginSupportProvider,
																						ShareBeanMgr shareBeanMgr) {
    // Setup RuntimeMode
    System.setProperty(PF4J_MODE, properties.getRuntimeMode().toString());

    // Setup Plugin folder
    String pluginsRoot = StringUtils.hasText(properties.getPluginsRoot()) ? properties.getPluginsRoot() : "plugins";

    System.setProperty(PF4J_PLUGINS_DIR, pluginsRoot);

    Pf4bootPluginManager pluginManager = new Pf4bootPluginManagerImpl(applicationContext, properties,
				pluginSupportProvider, shareBeanMgr, new File(pluginsRoot).toPath());

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