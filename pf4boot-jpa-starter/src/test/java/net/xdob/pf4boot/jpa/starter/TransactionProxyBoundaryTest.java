package net.xdob.pf4boot.jpa.starter;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.junit.Assert.assertEquals;

public class TransactionProxyBoundaryTest {

  @Test
  public void selfInvocationDoesNotApplyRequiresNewProxyBoundary() {
    AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(TransactionConfig.class);
    try {
      SelfInvocationService service = context.getBean(SelfInvocationService.class);
      CountingTransactionManager transactionManager =
          context.getBean(CountingTransactionManager.class);

      service.outer();

      assertEquals(1, transactionManager.getBeginCount());
    } finally {
      context.close();
    }
  }

  @Test
  public void separateBeanAppliesRequiresNewProxyBoundary() {
    AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(TransactionConfig.class);
    try {
      SeparateInvocationService service = context.getBean(SeparateInvocationService.class);
      CountingTransactionManager transactionManager =
          context.getBean(CountingTransactionManager.class);

      service.outer();

      assertEquals(2, transactionManager.getBeginCount());
    } finally {
      context.close();
    }
  }

  @Configuration
  @EnableTransactionManagement(proxyTargetClass = true)
  public static class TransactionConfig {
    @Bean
    public CountingTransactionManager tx() {
      return new CountingTransactionManager();
    }

    @Bean
    public SelfInvocationService selfInvocationService() {
      return new SelfInvocationService();
    }

    @Bean
    public SeparateInvocationService separateInvocationService(RequiresNewWorker worker) {
      return new SeparateInvocationService(worker);
    }

    @Bean
    public RequiresNewWorker requiresNewWorker() {
      return new RequiresNewWorker();
    }
  }

  public static class SelfInvocationService {
    @Transactional(transactionManager = "tx")
    public void outer() {
      innerRequiresNew();
    }

    @Transactional(transactionManager = "tx", propagation = Propagation.REQUIRES_NEW)
    public void innerRequiresNew() {
    }
  }

  public static class SeparateInvocationService {
    private final RequiresNewWorker worker;

    SeparateInvocationService(RequiresNewWorker worker) {
      this.worker = worker;
    }

    @Transactional(transactionManager = "tx")
    public void outer() {
      this.worker.innerRequiresNew();
    }
  }

  public static class RequiresNewWorker {
    @Transactional(transactionManager = "tx", propagation = Propagation.REQUIRES_NEW)
    public void innerRequiresNew() {
    }
  }

  public static class CountingTransactionManager extends AbstractPlatformTransactionManager {
    private int beginCount;

    int getBeginCount() {
      return beginCount;
    }

    @Override
    protected Object doGetTransaction() throws TransactionException {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition)
        throws TransactionException {
      this.beginCount++;
    }

    @Override
    protected Object doSuspend(Object transaction) throws TransactionException {
      return transaction;
    }

    @Override
    protected void doResume(Object transaction, Object suspendedResources)
        throws TransactionException {
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
    }
  }
}
