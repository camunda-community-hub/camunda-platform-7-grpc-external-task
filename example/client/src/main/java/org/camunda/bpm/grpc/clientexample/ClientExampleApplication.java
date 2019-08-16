package org.camunda.bpm.grpc.clientexample;

import org.camunda.bpm.grpc.client.ExternalTaskClientGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

@SpringBootApplication
public class ClientExampleApplication {

  private static final Logger log = LoggerFactory.getLogger(ClientExampleApplication.class);
  static final String WORKER_ID = "grpc-worker";

  @Autowired
  MyExternalTaskHandler handler;

  @Autowired ExternalTaskClientGrpc myClient;

  public static void main(String[] args) throws IOException {
    SpringApplication.run(ClientExampleApplication.class, args);
  }

  @Bean
  CommandLineRunner runner(){
    return args -> {
      myClient.start();
      log.info("PRESS ANY KEY TO EXIT");
      System.in.read();
      myClient.stop();
    };
  }

  @Bean
  ExternalTaskClientGrpc clientGrpc(){
    return new ExternalTaskClientGrpc("fancyTask", WORKER_ID, handler);
  }

  @Bean
  MyExternalTaskHandler handler(){
    return new MyExternalTaskHandler();
  }

}
