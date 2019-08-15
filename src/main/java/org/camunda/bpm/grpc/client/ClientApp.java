package org.camunda.bpm.grpc.client;

public class ClientApp {

  public static void main(String[] args) throws InterruptedException {
    ExternalTaskClientGrpc myClient = new ExternalTaskClientGrpc("fancyTask");

    myClient.start();

    Thread.sleep(15000L);

    myClient.stop();
  }
}
