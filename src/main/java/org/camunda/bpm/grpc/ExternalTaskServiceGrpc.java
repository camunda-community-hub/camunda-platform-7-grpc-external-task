package org.camunda.bpm.grpc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.camunda.bpm.grpc.ExternalTaskGrpc.ExternalTaskImplBase;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.beans.factory.annotation.Autowired;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@GRpcService
public class ExternalTaskServiceGrpc extends ExternalTaskImplBase {

  @Autowired
  private ExternalTaskService externalTaskService;

  private String workerId = "grpc-worker";
  private long lockTimeout = 30000L;

  private Map<String, List<StreamObserver<FetchAndLockReply>>> waitingClients = new HashMap<>();

  @Override
  public StreamObserver<FetchAndLockRequest> fetchAndLock(StreamObserver<FetchAndLockReply> responseObserver) {
    StreamObserver<FetchAndLockRequest> requestObserver = new StreamObserver<FetchAndLockRequest>() {

      @Override
      public void onNext(FetchAndLockRequest request) {
        String topicName = request.getTopicName();
        List<LockedExternalTask> lockedTasks = externalTaskService
            .fetchAndLock(1, workerId)
            .topic(topicName, lockTimeout)
            .execute();
        if (lockedTasks.isEmpty()) {
          // if no external tasks locked => save the response observer and
          // notify later when external task created for the topic in the engine
          if (!waitingClients.containsKey(topicName)) {
            waitingClients.put(topicName, new ArrayList<>());
          }
          waitingClients.get(topicName).add(responseObserver);
        } else {
          FetchAndLockReply.Builder replyBuilder = FetchAndLockReply.newBuilder().setId(lockedTasks.get(0).getId());
          responseObserver.onNext(replyBuilder.build());
        }
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

}
