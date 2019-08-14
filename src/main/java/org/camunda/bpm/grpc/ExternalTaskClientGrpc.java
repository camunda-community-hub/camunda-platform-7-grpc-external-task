package org.camunda.bpm.grpc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExternalTaskClientGrpc {
  public static void main(String[] args) throws InterruptedException {

    CountDownLatch latch = new CountDownLatch(15);
    FetchAndLockRequest request = FetchAndLockRequest.newBuilder().setTopicName("fancy").build();

    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565).usePlaintext()
        .build();

    ExternalTaskGrpc.ExternalTaskStub stub = ExternalTaskGrpc.newStub(channel);
    final StreamObserver<FetchAndLockRequest> requestObserver = stub.fetchAndLock(new StreamObserver<FetchAndLockReply>() {

      @Override
      public void onNext(FetchAndLockReply var1) {
        log.info("Got a task, done it: " + var1.getId());
      }

      @Override
      public void onError(Throwable var1) {
        log.info("Oh oh, error on sever", var1);
        latch.countDown();
      }

      @Override
      public void onCompleted() {
        log.info("Server is done");
        latch.countDown();
      }
    });

    try {
      for (int i = 0; i < 15; i++) {
        requestObserver.onNext(request);
        Thread.sleep(300);
        if (latch.getCount() == 0) {
          return;
        }
      }
    } catch (Exception e) {
      requestObserver.onError(e);
    }

    requestObserver.onCompleted();

    channel.shutdown();

    latch.await(1, TimeUnit.MINUTES);
  }


}
