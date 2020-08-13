package org.camunda.bpm.grpc.clientexample;

import java.io.IOException;
import java.util.Random;

import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.grpc.client.impl.ExternalTaskClientBuilderImplGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ClientExampleApplication {

  private static final Logger log = LoggerFactory.getLogger(ClientExampleApplication.class);
  static final String WORKER_ID_PREFIX = "grpc-worker-";

  @Autowired
  ExternalTaskHandler handler;

  @Autowired
  ExternalTaskClient myClient;

  public static void main(String[] args) throws IOException {
    SpringApplication.run(ClientExampleApplication.class, args);
  }

  @Bean
  CommandLineRunner runner() {
    return args -> {
      myClient.subscribe("fancyTask").handler(handler).open();
      myClient.start();
      log.info("PRESS ANY KEY TO EXIT");
      System.in.read();
      myClient.stop();
    };
  }

  @Bean
  ExternalTaskClient clientGrpc() {
    return new ExternalTaskClientBuilderImplGrpc()
        .baseUrl("localhost:6565")
        .workerId(WORKER_ID_PREFIX + new Random().nextInt(100))
        .lockDuration(5000L)
        .disableAutoFetching()
        .build();
  }

  @Bean
  ExternalTaskHandler handler() {
    return new MyExternalTaskHandler();
  }

}
