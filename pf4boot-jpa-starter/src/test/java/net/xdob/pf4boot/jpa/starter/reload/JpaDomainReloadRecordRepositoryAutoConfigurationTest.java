package net.xdob.pf4boot.jpa.starter.reload;

import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecordRepository;
import net.xdob.pf4boot.jpa.starter.Pf4bootJpaGovernanceProperties;
import net.xdob.pf4boot.jpa.starter.Pf4bootJpaProperties;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class JpaDomainReloadRecordRepositoryAutoConfigurationTest {

  @Test
  public void createsMemoryRecordRepositoryByDefault() {
    JpaDomainReloadAutoConfiguration configuration = new JpaDomainReloadAutoConfiguration();

    JpaDomainReloadRecordRepository repository =
        configuration.jpaDomainReloadRecordRepository(
            new Pf4bootJpaProperties(),
            new Pf4bootJpaGovernanceProperties(),
            new MockEnvironment());

    assertTrue(repository instanceof InMemoryJpaDomainReloadRecordRepository);
  }

  @Test
  public void createsFileRecordRepositoryWhenConfigured() throws Exception {
    Path directory = Files.createTempDirectory("pf4boot-jpa-reload-store");
    Pf4bootJpaProperties properties = new Pf4bootJpaProperties();
    properties.getDomainReload().getRecordStore().setType("file");
    properties.getDomainReload().getRecordStore().setDirectory(directory.toString());

    JpaDomainReloadAutoConfiguration configuration = new JpaDomainReloadAutoConfiguration();
    JpaDomainReloadRecordRepository repository =
        configuration.jpaDomainReloadRecordRepository(
            properties,
            new Pf4bootJpaGovernanceProperties(),
            new MockEnvironment()
                .withProperty("pf4boot.plugin.jpa.domain-reload.record-store.type", "file"));

    assertTrue(repository instanceof FileJpaDomainReloadRecordRepository);
  }

  @Test
  public void createsFileRecordRepositoryFromGovernancePrefix() throws Exception {
    Path directory = Files.createTempDirectory("pf4boot-jpa-reload-governance-store");
    Pf4bootJpaProperties legacy = new Pf4bootJpaProperties();
    Pf4bootJpaGovernanceProperties governance = new Pf4bootJpaGovernanceProperties();
    governance.getReload().getRecordStore().setType("file");
    governance.getReload().getRecordStore().setDirectory(directory.toString());

    JpaDomainReloadAutoConfiguration configuration = new JpaDomainReloadAutoConfiguration();
    JpaDomainReloadRecordRepository repository =
        configuration.jpaDomainReloadRecordRepository(
            legacy,
            governance,
            new MockEnvironment()
                .withProperty("spring.pf4boot.jpa.reload.record-store.type", "file"));

    assertTrue(repository instanceof FileJpaDomainReloadRecordRepository);
  }
}
