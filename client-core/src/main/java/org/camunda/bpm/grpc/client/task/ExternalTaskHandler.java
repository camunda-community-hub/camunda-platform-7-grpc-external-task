package org.camunda.bpm.grpc.client.task;

import org.camunda.bpm.grpc.FetchAndLockResponse;

public interface ExternalTaskHandler {

  void handleTask(FetchAndLockResponse reply, ExternalTaskService service);

}
