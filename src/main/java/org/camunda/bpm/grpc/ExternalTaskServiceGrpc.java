package org.camunda.bpm.grpc;

import org.camunda.bpm.grpc.ExternalTaskGrpc.ExternalTaskImplBase;
import org.lognet.springboot.grpc.GRpcService;

import io.grpc.stub.StreamObserver;

@GRpcService
public class ExternalTaskServiceGrpc extends ExternalTaskImplBase {

  @Override
  public void fetchAndLock(FetchAndLockRequest request, StreamObserver<FetchAndLockReply> responseObserver) {
    FetchAndLockReply.Builder replyBuilder = FetchAndLockReply.newBuilder().setId("LockedTask1");
    responseObserver.onNext(replyBuilder.build());
    responseObserver.onCompleted();
  }
}
