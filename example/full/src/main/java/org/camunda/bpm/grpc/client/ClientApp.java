package org.camunda.bpm.grpc.client;

public class ClientApp {

  public static void main(String[] args) throws InterruptedException {
    ExternalTaskClientGrpc myClient = new ExternalTaskClientGrpc("fancyTask");

    myClient.start();

//    Thread.sleep(10000L);
//
//    myClient.stop();
//
//    Thread.sleep(5000L);
//
//    myClient.start();
  }

}
