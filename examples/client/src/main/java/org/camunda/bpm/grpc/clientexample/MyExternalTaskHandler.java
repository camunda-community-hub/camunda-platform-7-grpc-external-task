package org.camunda.bpm.grpc.clientexample;

import org.camunda.bpm.grpc.FetchAndLockResponse;
import org.camunda.bpm.grpc.client.task.ExternalTaskHandler;
import org.camunda.bpm.grpc.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyExternalTaskHandler implements ExternalTaskHandler {

  private static final Logger log = LoggerFactory.getLogger(MyExternalTaskHandler.class);

  public void handleTask(FetchAndLockResponse reply, ExternalTaskService service) {

    try {
      log.info("starting work on " + reply.getId());
      Thread.sleep(Math.round(Math.random() * 2000L));
    } catch (InterruptedException iex) {
      // fine
    }

    service.complete(reply);
  }

}
