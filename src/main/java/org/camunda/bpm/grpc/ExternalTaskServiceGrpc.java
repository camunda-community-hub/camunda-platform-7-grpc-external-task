package org.camunda.bpm.grpc;

import io.grpc.stub.StreamObserver;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.camunda.bpm.grpc.ExternalTaskGrpc.ExternalTaskImplBase;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@GRpcService
public class ExternalTaskServiceGrpc extends ExternalTaskImplBase {

  @Autowired
  private ExternalTaskService externalTaskService;

  private String workerId = "grpc-worker";
  private int lockTimeout = 30000;


  @Override
  public void fetchAndLock(FetchAndLockRequest request, StreamObserver<FetchAndLockReply> responseObserver) {

    List<LockedExternalTask> lockedExternalTasks = externalTaskService.fetchAndLock(1, workerId).topic(request.getTopicName(), lockTimeout).execute();

    FetchAndLockReply.Builder replyBuilder = FetchAndLockReply.newBuilder().setId(lockedExternalTasks.get(0).getId());
    responseObserver.onNext(replyBuilder.build());
    responseObserver.onCompleted();
  }
}
