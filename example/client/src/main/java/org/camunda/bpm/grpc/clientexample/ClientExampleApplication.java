package org.camunda.bpm.grpc.clientexample;

import org.camunda.bpm.grpc.client.ExternalTaskClientGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@SpringBootApplication
public class ClientExampleApplication {

  public static final Logger log = LoggerFactory.getLogger(ClientExampleApplication.class);

  public static void main(String[] args) throws IOException {
    SpringApplication.run(ClientExampleApplication.class, args);

    ExternalTaskClientGrpc myClient = new ExternalTaskClientGrpc("fancyTask");
    myClient.start();

    log.info("PRESS ANY KEY TO CLOSE");
    System.in.read();

    myClient.stop();

  }

}
