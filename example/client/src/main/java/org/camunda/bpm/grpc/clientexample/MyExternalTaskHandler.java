package org.camunda.bpm.grpc.clientexample;

import io.grpc.stub.StreamObserver;
import org.camunda.bpm.grpc.CompleteRequest;
import org.camunda.bpm.grpc.CompleteResponse;
import org.camunda.bpm.grpc.ExternalTaskGrpc;
import org.camunda.bpm.grpc.FetchAndLockReply;
import org.camunda.bpm.grpc.client.ExternalTaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyExternalTaskHandler implements ExternalTaskHandler {

  private static final Logger log = LoggerFactory.getLogger(MyExternalTaskHandler.class);

  private String workerId;

  public void handleTask(FetchAndLockReply reply, ExternalTaskGrpc.ExternalTaskStub service) {

    service.complete(CompleteRequest.newBuilder().setWorkerId(workerId).setId(reply.getId()).build(), new StreamObserver<CompleteResponse>() {
      @Override
      public void onNext(CompleteResponse completeResponse) {
        log.info("Task completed with " + completeResponse.getStatus());
      }

      @Override
      public void onError(Throwable throwable) {
        log.error("Oh no, could not complete the task (server error)", throwable);
      }

      @Override
      public void onCompleted() {}
    });
  }

  @Override
  public void setWorkerId(String workerId) {
    this.workerId = workerId;
  }
}
