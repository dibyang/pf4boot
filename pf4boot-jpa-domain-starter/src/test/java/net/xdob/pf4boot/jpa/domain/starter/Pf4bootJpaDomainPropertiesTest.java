package net.xdob.pf4boot.jpa.domain.starter;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class Pf4bootJpaDomainPropertiesTest {

  @Test
  public void defaultBeanNamesUseDomainId() {
    Pf4bootJpaDomainProperties properties = new Pf4bootJpaDomainProperties();
    properties.setId("order");

    assertEquals("domain.order.dataSource", properties.resolveDataSourceName());
    assertEquals("domain.order.entityManagerFactory", properties.resolveEntityManagerFactoryName());
    assertEquals("domain.order.transactionManager", properties.resolveTransactionManagerName());
  }

  @Test
  public void entityPackagesAreTrimmedAndRequired() {
    Pf4bootJpaDomainProperties properties = new Pf4bootJpaDomainProperties();
    properties.setId("order");
    properties.setEntityPackages(Arrays.asList(" net.xdob.demo.order.entity ", "", null));

    assertArrayEquals(
        new String[]{"net.xdob.demo.order.entity"},
        properties.resolveEntityPackages());
  }

  @Test
  public void emptyEntityPackagesFailFast() {
    Pf4bootJpaDomainProperties properties = new Pf4bootJpaDomainProperties();
    properties.setId("order");
    properties.setEntityPackages(Collections.emptyList());

    try {
      properties.resolveEntityPackages();
      fail("empty entity packages should fail");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("[PJF-005]"));
    }
  }
}
