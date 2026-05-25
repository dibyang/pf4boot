package net.xdob.pf4boot.spring.boot;


import net.xdob.pf4boot.annotation.Export;
import net.xdob.pf4boot.internal.*;
import net.xdob.pf4boot.*;
import net.xdob.pf4boot.modal.SharingScope;
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
  @ConditionalOnMissingBean(AutoExportMgr.class)
  public DefaultAutoExportMgr autoExportMgr(){
    return new DefaultAutoExportMgr();
  }

  @Bean
  @ConditionalOnMissingBean(ShareBeanMgr.class)
  public DefaultShareBeanMgr shareBeanMgr(AutoExportMgr autoExportMgr){
    return new DefaultShareBeanMgr(autoExportMgr);
  }

  @Bean
  @Lazy
  @ConditionalOnMissingBean
  public Pf4bootPluginManager pluginManager(ApplicationContext applicationContext,  Pf4bootProperties properties,
																						ObjectProvider<Pf4bootPluginSupport> pluginSupports,
																						ShareBeanMgr shareBeanMgr) {
    // Setup RuntimeMode
    System.setProperty(PF4J_MODE, properties.getRuntimeMode().toString());

    // Setup Plugin folder
    String pluginsRoot = StringUtils.hasText(properties.getPluginsRoot()) ? properties.getPluginsRoot() : "plugins";

    System.setProperty(PF4J_PLUGINS_DIR, pluginsRoot);

    Pf4bootPluginManager pluginManager = new Pf4bootPluginManagerImpl(applicationContext, properties,
				compositePluginSupport(pluginSupports), shareBeanMgr, new File(pluginsRoot).toPath());

    pluginManager.setProfiles(properties.getPluginProfiles());
    pluginManager.presetProperties(flatProperties(properties.getPluginProperties()));
    pluginManager.setExactVersionAllowed(properties.isExactVersionAllowed());
    pluginManager.setSystemVersion(properties.getSystemVersion());

    return pluginManager;
  }

  private Pf4bootPluginSupport compositePluginSupport(ObjectProvider<Pf4bootPluginSupport> pluginSupports) {
    List<Pf4bootPluginSupport> supports = pluginSupports.orderedStream()
        .collect(java.util.stream.Collectors.toList());
    if (supports.isEmpty()) {
      return new Pf4bootPluginSupport() {
      };
    }
    if (supports.size() == 1) {
      return supports.get(0);
    }
    return new Pf4bootPluginSupport() {
      @Override
      public int getPriority() {
        return Pf4bootPluginSupport.DEFAULT_PRIORITY;
      }

      @Override
      public void initiatePlugin(Pf4bootPlugin pf4bootPlugin) {
        supports.forEach(support -> support.initiatePlugin(pf4bootPlugin));
      }

      @Override
      public void initiatedPlugin(Pf4bootPlugin pf4bootPlugin) {
        supports.forEach(support -> support.initiatedPlugin(pf4bootPlugin));
      }

      @Override
      public void startPlugin(Pf4bootPlugin pf4bootPlugin) {
        supports.forEach(support -> support.startPlugin(pf4bootPlugin));
      }

      @Override
      public void startedPlugin(Pf4bootPlugin pf4bootPlugin) {
        supports.forEach(support -> support.startedPlugin(pf4bootPlugin));
      }

      @Override
      public void stopPlugin(Pf4bootPlugin pf4bootPlugin) {
        supports.forEach(support -> support.stopPlugin(pf4bootPlugin));
      }

      @Override
      public void stoppedPlugin(Pf4bootPlugin pf4bootPlugin) {
        supports.forEach(support -> support.stoppedPlugin(pf4bootPlugin));
      }

      @Override
      public void releasePlugin(Pf4bootPlugin pf4bootPlugin) {
        supports.forEach(support -> support.releasePlugin(pf4bootPlugin));
      }

      @Override
      public void deletePlugin(Pf4bootPlugin pf4bootPlugin) {
        supports.forEach(support -> support.deletePlugin(pf4bootPlugin));
      }

      @Override
      public void deletedPlugin(Pf4bootPlugin pf4bootPlugin) {
        supports.forEach(support -> support.deletedPlugin(pf4bootPlugin));
      }
    };
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
