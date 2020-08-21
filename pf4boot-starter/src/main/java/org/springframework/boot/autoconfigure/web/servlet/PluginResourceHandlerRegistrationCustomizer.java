package org.springframework.boot.autoconfigure.web.servlet;

import com.ls.pf4boot.internal.PluginResourceResolver;
import com.ls.pf4boot.spring.boot.Pf4bootPluginStateChangedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.web.ResourceProperties;
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

  @Autowired
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  private ResourceProperties resourceProperties = new ResourceProperties();

  @Autowired(required = false)
  @Qualifier("sbpResourceCache")
  private Cache sbpResourceCache;

  @Autowired
  private PluginResourceResolver pluginResourceResolver;

  @Override
  public void customize(ResourceHandlerRegistration registration) {
    if (sbpResourceCache == null) {
      sbpResourceCache = new ConcurrentMapCache(DEFAULT_CACHE_NAME);
    }
    ResourceProperties.Chain properties = this.resourceProperties.getChain();
    ResourceChainRegistration chain = registration.resourceChain(properties.isCache(), sbpResourceCache);

    chain.addResolver(pluginResourceResolver);

    ResourceProperties.Strategy strategy = properties.getStrategy();
    if (properties.isCompressed()) {
      chain.addResolver(new EncodedResourceResolver());
    }
    if (strategy.getFixed().isEnabled() || strategy.getContent().isEnabled()) {
      chain.addResolver(getVersionResourceResolver(strategy));
    }
    if (properties.isHtmlApplicationCache()) {
      chain.addTransformer(new AppCacheManifestTransformer());
    }
  }

  private ResourceResolver getVersionResourceResolver(ResourceProperties.Strategy properties) {
    VersionResourceResolver resolver = new VersionResourceResolver();
    if (properties.getFixed().isEnabled()) {
      String version = properties.getFixed().getVersion();
      String[] paths = properties.getFixed().getPaths();
      resolver.addFixedVersionStrategy(version, paths);
    }
    if (properties.getContent().isEnabled()) {
      String[] paths = properties.getContent().getPaths();
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
