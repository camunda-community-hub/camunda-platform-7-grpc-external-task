  package org.camunda.bpm.spring.boot.starter.grpc.externaltask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.camunda.bpm.grpc.FetchAndLockResponse;
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

  private List<Pair<FetchAndLockRequest, StreamObserver<FetchAndLockResponse>>> waitingClients = Collections.synchronizedList(new ArrayList<>());

  public void addWaitingClient(FetchAndLockRequest request, StreamObserver<FetchAndLockResponse> client) {
    waitingClients.add(Pair.of(request, client));
  }

  public void informClients() {
    log.info("Found {} pending requests", waitingClients.size());
    for (Iterator<Pair<FetchAndLockRequest, StreamObserver<FetchAndLockResponse>>> iterator = waitingClients.iterator(); iterator.hasNext();) {
      Pair<FetchAndLockRequest, StreamObserver<FetchAndLockResponse>> pair = iterator.next();
      List<LockedExternalTask> lockedTasks = ExternalTaskServiceGrpc.createQuery(pair.getLeft(), externalTaskService).execute();
      if (!lockedTasks.isEmpty()) {
        LockedExternalTask lockedExternalTask = lockedTasks.get(0);
        log.info("informed client about locked external task {}", lockedExternalTask.getId());
        iterator.remove();
        pair.getRight().onNext(FetchAndLockResponse.newBuilder()
            .setId(lockedExternalTask.getId())
            .setWorkerId(lockedExternalTask.getWorkerId())
            .build());
      }
    }
  }

  public void removeClientRequests(StreamObserver<FetchAndLockResponse> client) {
    log.info("Removing all pending requests for client");
    for (Iterator<Pair<FetchAndLockRequest, StreamObserver<FetchAndLockResponse>>> iterator = waitingClients.iterator(); iterator.hasNext();) {
      Pair<FetchAndLockRequest, StreamObserver<FetchAndLockResponse>> pair = iterator.next();
      if (pair.getRight().equals(client)) {
        iterator.remove();
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
