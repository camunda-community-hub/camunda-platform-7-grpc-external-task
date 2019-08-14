package org.camunda.bpm.grpc;

import org.camunda.bpm.grpc.ExternalTaskGrpc.ExternalTaskImplBase;
import org.lognet.springboot.grpc.GRpcService;

import io.grpc.stub.StreamObserver;

@GRpcService
public class ExternalTaskServiceGrpc extends ExternalTaskImplBase {

  @Override
  public StreamObserver<FetchAndLockRequest> fetchAndLock(StreamObserver<FetchAndLockReply> responseObserver) {
    StreamObserver<FetchAndLockRequest> requestObserver = new StreamObserver<FetchAndLockRequest>() {

      @Override
      public void onNext(FetchAndLockRequest value) {
        FetchAndLockReply.Builder replyBuilder = FetchAndLockReply.newBuilder().setId("LockedTask" + Math.random());
        responseObserver.onNext(replyBuilder.build());
        responseObserver.onCompleted();
      }

      @Override
      public void onError(Throwable t) {
        // nothing
      }

      @Override
      public void onCompleted() {
        // nothing
      }
    };
    return requestObserver;
  }

}
