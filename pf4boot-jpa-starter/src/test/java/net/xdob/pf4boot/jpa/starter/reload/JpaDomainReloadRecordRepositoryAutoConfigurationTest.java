package net.xdob.pf4boot.jpa.starter.reload;

import net.xdob.pf4boot.jpa.reload.JpaDomainReloadRecordRepository;
import net.xdob.pf4boot.jpa.starter.Pf4bootJpaProperties;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class JpaDomainReloadRecordRepositoryAutoConfigurationTest {

  @Test
  public void createsMemoryRecordRepositoryByDefault() {
    JpaDomainReloadAutoConfiguration configuration = new JpaDomainReloadAutoConfiguration();

    JpaDomainReloadRecordRepository repository =
        configuration.jpaDomainReloadRecordRepository(new Pf4bootJpaProperties());

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
        configuration.jpaDomainReloadRecordRepository(properties);

    assertTrue(repository instanceof FileJpaDomainReloadRecordRepository);
  }
}
