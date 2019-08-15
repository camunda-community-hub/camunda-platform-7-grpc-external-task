package org.camunda.bpm.spring.boot.starter.grpc.externaltask;

import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.util.SingleConsumerCondition;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExternalTaskCreationListener implements Runnable {

  private WaitingClientInformer informer;
  private boolean isRunning = false;
  private Thread handlerThread = new Thread(this);
  private SingleConsumerCondition condition;

  public ExternalTaskCreationListener(WaitingClientInformer informer) {
    this.informer = informer;
  }

  @Override
  public void run() {
    while (isRunning) {
      try {
        log.info("Run external task listener...");
        informer.informClients();
        log.info("Let external task listener wait...");
        condition.await(500000L);
        log.info("External task listener woke up!");
      } catch (Exception e) {
        // what ever happens, don't leave the loop
      } finally {
        if (handlerThread.isInterrupted()) {
          Thread.currentThread().interrupt();
        }
      }
    }

  }

  public void start() {
    if (isRunning) {
      return;
    }

    isRunning = true;
    handlerThread.start();

    condition = new SingleConsumerCondition(handlerThread);
    ProcessEngineImpl.EXT_TASK_CONDITIONS.addConsumer(condition);
  }

  public void shutdown() {
    try {
      ProcessEngineImpl.EXT_TASK_CONDITIONS.removeConsumer(condition);
    } finally {
      isRunning = false;
      condition.signal();
    }

    try {
      handlerThread.join();
    } catch (InterruptedException e) {
      log.warn("Shutting down the handler thread failed", e);
    }
  }

}
