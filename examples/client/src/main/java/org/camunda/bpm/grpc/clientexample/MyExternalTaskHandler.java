package org.camunda.bpm.grpc.clientexample;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyExternalTaskHandler implements ExternalTaskHandler {

  private static final Logger log = LoggerFactory.getLogger(MyExternalTaskHandler.class);

  @Override
  public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
    try {
      log.info("starting work on " + externalTask.getId());
      Thread.sleep(Math.round(Math.random() * 2000L));
    } catch (InterruptedException iex) {
      // fine
    }

    externalTaskService.complete(externalTask);
  }



}
