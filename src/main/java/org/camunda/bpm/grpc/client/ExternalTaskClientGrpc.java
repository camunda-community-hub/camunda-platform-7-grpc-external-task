package org.camunda.bpm.grpc.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.grpc.ExternalTaskGrpc;
import org.camunda.bpm.grpc.FetchAndLockReply;
import org.camunda.bpm.grpc.FetchAndLockRequest;

import javax.annotation.PreDestroy;
import java.util.concurrent.Semaphore;

@Slf4j
public class ExternalTaskClientGrpc {

  private String topic;
  private ManagedChannel channel;
  private ExternalTaskGrpc.ExternalTaskStub stub;
  private FetchAndLockRequest request;
  private Semaphore semaphore;

  private boolean isRunning;

  public ExternalTaskClientGrpc(String topic){

    this.topic = topic;
    this.channel = ManagedChannelBuilder.forAddress("localhost", 6565).usePlaintext().build();
    this.stub = ExternalTaskGrpc.newStub(channel);
    this.request = FetchAndLockRequest.newBuilder().setTopicName(topic).build();
    this.semaphore = new Semaphore(0);

  }

  public void start() {

    log.info(">>>>>>>>>> Starting grpc client for topic " + topic);
    isRunning = true;

    final StreamObserver<FetchAndLockRequest> requestObserver = stub.fetchAndLock(new StreamObserver<FetchAndLockReply>() {

      @Override
      public void onNext(FetchAndLockReply var1) {
        log.info("Got a task, done it: " + var1.getId());
        semaphore.release();
      }

      @Override
      public void onError(Throwable var1) {
        log.info("Oh oh, error on sever", var1);
      }

      @Override
      public void onCompleted() {
        log.info("Server is done");
      }
    });

    while(isRunning) {

      requestObserver.onNext(request);
      try {
        semaphore.acquire();
      } catch (InterruptedException e) {
        log.error(e.getMessage());
      }
    }

    requestObserver.onCompleted();

  }

  public void stop(){
    log.info(">>>>>>>>>> Stopping grpc client for topic " + topic);
    isRunning = false;
  }

  @PreDestroy
  public void destroy(){
    channel.shutdown();
  }

}
