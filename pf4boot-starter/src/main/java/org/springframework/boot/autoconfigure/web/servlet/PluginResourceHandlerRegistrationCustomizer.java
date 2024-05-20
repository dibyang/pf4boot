package org.springframework.boot.autoconfigure.web.servlet;

import net.xdob.pf4boot.internal.PluginResourceResolver;
import net.xdob.pf4boot.spring.boot.Pf4bootPluginStateChangedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.context.ApplicationListener;
import org.springframework.web.servlet.config.annotation.ResourceChainRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.resource.AppCacheManifestTransformer;
import org.springframework.web.servlet.resource.EncodedResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.VersionResourceResolver;

public class PluginResourceHandlerRegistrationCustomizer implements
    WebMvcAutoConfiguration.ResourceHandlerRegistrationCustomizer,
    ApplicationListener<Pf4bootPluginStateChangedEvent> {

  private static final String DEFAULT_CACHE_NAME = "sbp-resource-chain-cache";

  //@Autowired
  //@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  private WebProperties resourceProperties;// = new WebProperties();

  //@Autowired(required = false)
  //@Qualifier("sbpResourceCache")
  private Cache sbpResourceCache;

  //@Autowired
  private PluginResourceResolver pluginResourceResolver;

  public PluginResourceHandlerRegistrationCustomizer(WebProperties resourceProperties, Cache sbpResourceCache, PluginResourceResolver pluginResourceResolver) {
    this.resourceProperties = resourceProperties;
    this.sbpResourceCache = sbpResourceCache;
    this.pluginResourceResolver = pluginResourceResolver;
  }

  @Override
  public void customize(ResourceHandlerRegistration registration) {
    if (sbpResourceCache == null) {
      sbpResourceCache = new ConcurrentMapCache(DEFAULT_CACHE_NAME);
    }
    WebProperties.Resources.Chain properties = this.resourceProperties.getResources().getChain();
    ResourceChainRegistration chain = registration.resourceChain(properties.isCache(), sbpResourceCache);

    chain.addResolver(pluginResourceResolver);

    WebProperties.Resources.Chain.Strategy strategy = properties.getStrategy();
    if (properties.isCompressed()) {
      chain.addResolver(new EncodedResourceResolver());
    }
    if (strategy.getFixed().isEnabled() || strategy.getContent().isEnabled()) {
      chain.addResolver(getVersionResourceResolver(strategy));
    }
    if (properties.isCache()) {
      chain.addTransformer(new AppCacheManifestTransformer());
    }
  }

  private ResourceResolver getVersionResourceResolver(WebProperties.Resources.Chain.Strategy strategy) {
    VersionResourceResolver resolver = new VersionResourceResolver();
    if (strategy.getFixed().isEnabled()) {
      String version = strategy.getFixed().getVersion();
      String[] paths = strategy.getFixed().getPaths();
      resolver.addFixedVersionStrategy(version, paths);
    }
    if (strategy.getContent().isEnabled()) {
      String[] paths = strategy.getContent().getPaths();
      resolver.addContentVersionStrategy(paths);
    }
    return resolver;
  }

  @Override
  public void onApplicationEvent(Pf4bootPluginStateChangedEvent event) {
    if (sbpResourceCache == null) return;
    sbpResourceCache.clear();
  }
}
