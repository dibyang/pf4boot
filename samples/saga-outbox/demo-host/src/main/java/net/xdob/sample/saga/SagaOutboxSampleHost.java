package net.xdob.sample.saga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Saga/Outbox 示例宿主。
 */
@SpringBootApplication
public class SagaOutboxSampleHost {

  public static void main(String[] args) {
    SpringApplication.run(SagaOutboxSampleHost.class, args);
  }
}
