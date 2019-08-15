package org.camunda.bpm.grpc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WaitingClientInformer {

  @Autowired
  ExternalTaskService externalTaskService;

  private List<Pair<FetchAndLockRequest, StreamObserver<FetchAndLockReply>>> waitingClients = Collections.synchronizedList(new ArrayList<>());

  public void addWaitingClient(FetchAndLockRequest request, StreamObserver<FetchAndLockReply> client) {
    waitingClients.add(Pair.of(request, client));
  }

  public void informClients() {
    log.info("Found {} pending requests", waitingClients.size());
    for (Iterator<Pair<FetchAndLockRequest, StreamObserver<FetchAndLockReply>>> iterator = waitingClients.iterator(); iterator.hasNext();) {
      Pair<FetchAndLockRequest, StreamObserver<FetchAndLockReply>> pair = iterator.next();
      // TODO build Java API request from request DTO
      List<LockedExternalTask> lockedTasks = externalTaskService
          .fetchAndLock(1, ExternalTaskServiceGrpc.WORKER_ID)
          .topic(pair.getLeft().getTopicName(), ExternalTaskServiceGrpc.LOCK_TIMEOUT)
          .execute();
      if (!lockedTasks.isEmpty()) {
        log.info("informed client about locked external task {}", lockedTasks.get(0).getId());
        iterator.remove();
        pair.getRight().onNext(FetchAndLockReply.newBuilder().setId(lockedTasks.get(0).getId()).build());
      }
    }
  }
}
