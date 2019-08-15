  package org.camunda.bpm.spring.boot.starter.grpc.externaltask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.camunda.bpm.grpc.FetchAndLockReply;
import org.camunda.bpm.grpc.FetchAndLockRequest;
import org.camunda.bpm.spring.boot.starter.event.PostDeployEvent;
import org.camunda.bpm.spring.boot.starter.event.PreUndeployEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WaitingClientInformer {

  @Autowired
  ExternalTaskService externalTaskService;

  private ExternalTaskCreationListener externalTaskCreationListener;

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
          .fetchAndLock(1, pair.getLeft().getWorkerId())
          .topic(pair.getLeft().getTopicName(), ExternalTaskServiceGrpc.LOCK_TIMEOUT)
          .execute();
      if (!lockedTasks.isEmpty()) {
        log.info("informed client about locked external task {}", lockedTasks.get(0).getId());
        iterator.remove();
        pair.getRight().onNext(FetchAndLockReply.newBuilder().setId(lockedTasks.get(0).getId()).build());
      }
    }
  }

  @EventListener
  public void onPostDeploy(PostDeployEvent event) {
    externalTaskCreationListener = new ExternalTaskCreationListener(this);
    externalTaskCreationListener.start();
  }

  @EventListener
  public void onPreUndeploy(PreUndeployEvent event) {
    if (externalTaskCreationListener != null) {
      externalTaskCreationListener.shutdown();
      externalTaskCreationListener = null;
    }
  }
}
