/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.grpc;

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
