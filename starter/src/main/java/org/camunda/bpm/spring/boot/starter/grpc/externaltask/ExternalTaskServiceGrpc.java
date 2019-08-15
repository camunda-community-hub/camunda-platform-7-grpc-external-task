package org.camunda.bpm.spring.boot.starter.grpc.externaltask;

import java.util.List;

import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.camunda.bpm.grpc.ExternalTaskGrpc.ExternalTaskImplBase;
import org.camunda.bpm.grpc.FetchAndLockReply;
import org.camunda.bpm.grpc.FetchAndLockRequest;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.beans.factory.annotation.Autowired;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@GRpcService
public class ExternalTaskServiceGrpc extends ExternalTaskImplBase {

  @Autowired
  private ExternalTaskService externalTaskService;

  @Autowired
  private WaitingClientInformer informer;

  public static final String WORKER_ID = "grpc-worker";
  public static final long LOCK_TIMEOUT = 30000L;

  @Override
  public StreamObserver<FetchAndLockRequest> fetchAndLock(StreamObserver<FetchAndLockReply> responseObserver) {
    StreamObserver<FetchAndLockRequest> requestObserver = new StreamObserver<FetchAndLockRequest>() {

      @Override
      public void onNext(FetchAndLockRequest request) {
        informClient(request, responseObserver);
      }

      @Override
      public void onError(Throwable t) {
        log.error("uh oh, server received error", t);
      }

      @Override
      public void onCompleted() {
        responseObserver.onCompleted();
      }
    };
    return requestObserver;
  }

  private void informClient(FetchAndLockRequest request, StreamObserver<FetchAndLockReply> client) {
    // TODO build Java API request from request DTO
    List<LockedExternalTask> lockedTasks = externalTaskService
        .fetchAndLock(1, WORKER_ID)
        .topic(request.getTopicName(), LOCK_TIMEOUT)
        .execute();
    if (lockedTasks.isEmpty()) {
      // if no external tasks locked => save the response observer and
      // notify later when external task created for the topic in the engine
      informer.addWaitingClient(request, client);
    } else {
      FetchAndLockReply.Builder replyBuilder = FetchAndLockReply.newBuilder().setId(lockedTasks.get(0).getId());
      client.onNext(replyBuilder.build());
    }
  }
}
