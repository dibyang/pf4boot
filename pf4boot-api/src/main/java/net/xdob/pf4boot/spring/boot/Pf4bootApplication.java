package net.xdob.pf4boot.spring.boot;

import net.xdob.pf4boot.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.Pf4bootAnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 已废弃
 * @deprecated
 */
@Deprecated
public class Pf4bootApplication extends SpringApplication implements PluginApplication {
  private final static Logger log = LoggerFactory.getLogger(Pf4bootApplication.class);


  public static final String[] DEFAULT_EXCLUDE_APPLICATION_LISTENERS = {
      "org.springframework.cloud.bootstrap.BootstrapApplicationListener",
      "org.springframework.cloud.bootstrap.LoggingSystemShutdownListener",
      "org.springframework.cloud.context.restart.RestartListener",
  };
  private final Pf4bootPlugin plugin;

  private final ApplicationContext mainApplicationContext;

  private final ClassLoader pluginClassLoader;


  private final Map<String, Object> presetProperties = new HashMap<>();

  private List<String> pluginFirstClasses;

  private List<String> pluginOnlyResources;

  /**
   * Constructor should be the only thing need to take care for this Class.
   * Generally new an instance and {@link #run(String...)} it
   * in {@link Pf4bootPluginService#createSpringBootstrap()} method.
   *
   * @param primarySources {@link SpringApplication} that annotated with @SpringBootApplication
   */
  @SuppressWarnings("JavadocReference")
  public Pf4bootApplication(Pf4bootPlugin plugin,
                            Class<?>... primarySources) {
    super(new DefaultResourceLoader(plugin.getWrapper().getPluginClassLoader()), primarySources);
    this.plugin = plugin;
    this.pluginClassLoader = plugin.getWrapper().getPluginClassLoader();
    Pf4bootPluginManager pluginManager = TypeWrapper.wrapper(plugin.getWrapper().getPluginManager(), Pf4bootPluginManager.class)
        .orElse(null);
    this.mainApplicationContext = pluginManager.getApplicationContext();

    Map<String, Object> presetProperties = pluginManager.getPresetProperties();
    if (presetProperties != null) {
      this.presetProperties.putAll(presetProperties);
    }
    this.presetProperties.put(EnableAutoConfiguration.ENABLED_OVERRIDE_PROPERTY,false);

  }


  /**
   * Properties that need to be set when this app is started as a plugin.
   * Note that this method only takes effect before {@link #run(String...)} method.
   */
  public Pf4bootApplication addPresetProperty(String name, Object value) {
    this.presetProperties.put(name, value);
    return this;
  }

  @Override
  protected void configurePropertySources(ConfigurableEnvironment environment,
                                          String[] args) {
    super.configurePropertySources(environment, args);
    TypeWrapper.wrapper(plugin.getWrapper().getPluginManager(), Pf4bootPluginManager.class)
        .ifPresent(pluginManager->{
          String[] profiles = pluginManager.getProfiles();
          if (profiles != null && profiles.length > 0) environment.setActiveProfiles(profiles);
        });
    environment.getPropertySources().addLast(new ExcludeConfigurations());
  }


  @Override
  protected void bindToSpringApplication(ConfigurableEnvironment environment) {
    super.bindToSpringApplication(environment);
    pluginFirstClasses = new ArrayList<>();
    String pluginFirstClassesProp;
    int i = 0;
    do {
      pluginFirstClassesProp = getProperties(environment, "pluginFirstClasses", i++);
      if (pluginFirstClassesProp != null) {
        pluginFirstClasses.add(pluginFirstClassesProp);
      }
    } while (pluginFirstClassesProp != null);

    pluginOnlyResources = new ArrayList<>();
    String pluginOnlyResourcesProp;
    i = 0;
    do {
      pluginOnlyResourcesProp = getProperties(environment, "pluginOnlyResources", i++);
      if (pluginOnlyResourcesProp != null) {
        pluginOnlyResources.add(pluginOnlyResourcesProp);
      }
    } while (pluginOnlyResourcesProp != null);
  }



  protected String[] getExcludeApplicationListeners() {
    return DEFAULT_EXCLUDE_APPLICATION_LISTENERS;
  }

  @Override
  public void setListeners(Collection<? extends ApplicationListener<?>> listeners) {
    super.setListeners(listeners
        .stream()
        .filter(listener -> !ArrayUtils.contains(
            getExcludeApplicationListeners(), listener.getClass().getName()))
        .collect(Collectors.toList()));
  }

  @Override
  public ConfigurableApplicationContext createApplicationContext() {
    setWebApplicationType(WebApplicationType.NONE);

    if (pluginClassLoader instanceof PluginClassLoader4boot) {
      if (pluginFirstClasses != null) {
        ((PluginClassLoader4boot) pluginClassLoader).setPluginFirstClasses(pluginFirstClasses);
      }
      if (pluginOnlyResources != null) {
        ((PluginClassLoader4boot) pluginClassLoader).setPluginOnlyResources(pluginOnlyResources);
      }
    }

    DefaultListableBeanFactory beanFactory = new PluginListableBeanFactory(pluginClassLoader);
    AnnotationConfigApplicationContext applicationContext = new Pf4bootAnnotationConfigApplicationContext(beanFactory,plugin);
    applicationContext.setParent(mainApplicationContext);

    applicationContext.setClassLoader(pluginClassLoader);
    applicationContext.getBeanFactory().registerSingleton(BEAN_PLUGIN, plugin);
    applicationContext.getBeanFactory().autowireBean(plugin);
    return applicationContext;
  }

  @Override
  protected void applyInitializers(ConfigurableApplicationContext context){
    super.applyInitializers(context);
    context.setId(this.plugin.getWrapper().getPluginId());

  }


  private String getProperties(Environment env, String propName, int index) {
    String prop = env.getProperty(String.format("pf4boot-plugin.%s[%s]", propName, index));
    if (prop == null) prop = env.getProperty(String.format("pf4boot-plugin.%s.%s", propName, index));
    if (prop == null) prop = env.getProperty(String.format("pf4boot-plugin.%s[%s]",
        String.join("-", StringUtils.splitByCharacterTypeCamelCase(propName)).toLowerCase(), index));
    if (prop == null) prop = env.getProperty(String.format("pf4boot-plugin.%s.%s",
        String.join("-", StringUtils.splitByCharacterTypeCamelCase(propName)).toLowerCase(), index));
    return prop;
  }

  @Override
  public Pf4bootPlugin getPlugin() {
    return this.plugin;
  }

  public class ExcludeConfigurations extends MapPropertySource {
    ExcludeConfigurations() {
      super("Exclude Configurations", presetProperties);
    }
  }

}
