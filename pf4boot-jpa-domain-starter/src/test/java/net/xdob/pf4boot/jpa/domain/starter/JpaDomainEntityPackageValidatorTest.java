package net.xdob.pf4boot.jpa.domain.starter;

import org.junit.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JpaDomainEntityPackageValidatorTest {

  @Test
  public void validatorFindsEntityFromVisiblePackage() {
    JpaDomainEntityPackageValidator validator =
        new JpaDomainEntityPackageValidator(new DefaultResourceLoader());

    int count = validator.validate(
        "sample",
        new String[]{"net.xdob.pf4boot.jpa.domain.starter.model"});

    assertEquals(1, count);
  }

  @Test
  public void validatorAllowsVisiblePackageWithoutEntityAsWarningPhase() {
    JpaDomainEntityPackageValidator validator =
        new JpaDomainEntityPackageValidator(new DefaultResourceLoader());

    int count = validator.validate(
        "sample",
        new String[]{"net.xdob.pf4boot.jpa.domain.starter.noentity"});

    assertEquals(0, count);
  }

  @Test
  public void validatorRejectsInvisiblePackage() {
    JpaDomainEntityPackageValidator validator =
        new JpaDomainEntityPackageValidator(new DefaultResourceLoader());

    try {
      validator.validate(
          "sample",
          new String[]{"net.xdob.pf4boot.jpa.domain.starter.missing"});
      fail("invisible package should fail");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("[PJF-008]"));
      assertTrue(e.getMessage().contains("net.xdob.pf4boot.jpa.domain.starter.missing"));
    }
  }
}
