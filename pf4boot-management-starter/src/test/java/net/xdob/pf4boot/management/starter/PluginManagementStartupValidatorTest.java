package net.xdob.pf4boot.management.starter;

import net.xdob.pf4boot.management.PluginManagementAuthorizer;
import net.xdob.pf4boot.management.PluginManagementErrorCode;
import net.xdob.pf4boot.management.PluginManagementMode;
import net.xdob.pf4boot.management.PluginManagementOperation;
import net.xdob.pf4boot.management.PluginManagementPrincipal;
import net.xdob.pf4boot.management.PluginManagementRequest;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PluginManagementStartupValidatorTest {

  @Test
  public void localModeRequiresTokenByDefault() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setEnabled(true);
    properties.setMode(PluginManagementMode.LOCAL_TOKEN);

    PluginManagementStartupValidator validator =
        new PluginManagementStartupValidator(properties, providerWithBeans());
    try {
      validator.validate();
      fail("expected missing-token validation error");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.INVALID_REQUEST, e.getCode());
      assertEquals(400, e.getStatusCode());
    }
  }

  @Test
  public void localModePassesWhenCustomAuthorizerExistsWithoutToken() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setEnabled(true);
    properties.setMode(PluginManagementMode.LOCAL_TOKEN);

    PluginManagementStartupValidator validator =
        new PluginManagementStartupValidator(properties, providerWithBeans(sampleAuthorizer()));
    validator.validate();
  }

  @Test
  public void disabledModeSkipValidationWhenManagementNotEnabled() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setEnabled(false);
    properties.setMode(PluginManagementMode.LOCAL_TOKEN);

    PluginManagementStartupValidator validator =
        new PluginManagementStartupValidator(properties, providerWithBeans());
    validator.validate();
  }

  @Test
  public void localModePassesWhenTokenConfigured() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setEnabled(true);
    properties.setMode(PluginManagementMode.LOCAL_TOKEN);
    properties.setToken("sample-token");

    PluginManagementStartupValidator validator =
        new PluginManagementStartupValidator(properties, providerWithBeans());
    validator.validate();
  }

  @Test
  public void remoteModeRequiresAuthorizerBean() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setEnabled(true);
    properties.setMode(PluginManagementMode.REMOTE_DELEGATED);

    PluginManagementStartupValidator validator =
        new PluginManagementStartupValidator(properties, providerWithBeans());
    try {
      validator.validate();
      fail("expected remote mode validation error");
    } catch (PluginManagementException e) {
      assertEquals(PluginManagementErrorCode.INVALID_REQUEST, e.getCode());
      assertEquals(400, e.getStatusCode());
    }
  }

  @Test
  public void remoteModePassesWithCustomAuthorizer() {
    Pf4bootManagementProperties properties = new Pf4bootManagementProperties();
    properties.setEnabled(true);
    properties.setMode(PluginManagementMode.REMOTE_DELEGATED);

    PluginManagementStartupValidator validator =
        new PluginManagementStartupValidator(properties, providerWithBeans(sampleAuthorizer()));
    validator.validate();
  }

  private ObjectProvider<PluginManagementAuthorizer> providerWithBeans(PluginManagementAuthorizer... authorizers) {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    for (int i = 0; i < authorizers.length; i++) {
      beanFactory.registerSingleton("auth-" + i, authorizers[i]);
    }
    return beanFactory.getBeanProvider(PluginManagementAuthorizer.class);
  }

  private PluginManagementAuthorizer sampleAuthorizer() {
    return new PluginManagementAuthorizer() {
      @Override
      public PluginManagementPrincipal authenticate(PluginManagementRequest request) {
        return null;
      }

      @Override
      public void authorize(PluginManagementPrincipal principal, PluginManagementOperation operation) {
        // no-op for startup validator test
      }
    };
  }
}
