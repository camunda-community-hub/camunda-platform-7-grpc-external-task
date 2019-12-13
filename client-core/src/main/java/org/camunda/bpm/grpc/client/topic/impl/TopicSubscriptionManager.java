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
package org.camunda.bpm.grpc.client.topic.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.camunda.bpm.grpc.ExternalTaskGrpc;
import org.camunda.bpm.grpc.FetchAndLockRequest;
import org.camunda.bpm.grpc.FetchAndLockResponse;
import org.camunda.bpm.grpc.ExternalTaskGrpc.ExternalTaskStub;
import org.camunda.bpm.grpc.FetchAndLockRequest.FetchExternalTaskTopic;
import org.camunda.bpm.grpc.client.impl.ExternalTaskClientLogger;
import org.camunda.bpm.grpc.client.task.ExternalTaskHandler;
import org.camunda.bpm.grpc.client.task.ExternalTaskService;
import org.camunda.bpm.grpc.client.task.impl.ExternalTaskServiceImpl;
import org.camunda.bpm.grpc.client.topic.TopicSubscription;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

public class TopicSubscriptionManager implements Runnable {

  protected static final TopicSubscriptionManagerLogger LOG = ExternalTaskClientLogger.TOPIC_SUBSCRIPTION_MANAGER_LOGGER;

  protected AtomicBoolean isRunning = new AtomicBoolean(false);

  protected CopyOnWriteArrayList<TopicSubscription> subscriptions;
  protected List<FetchExternalTaskTopic> taskTopicRequests;
  protected Map<String, ExternalTaskHandler> externalTaskHandlers;

  private ManagedChannel channel;
  private ExternalTaskStub stub;

  private Semaphore semaphore;

  private ExternalTaskService service;

  private StreamObserver<FetchAndLockRequest> requestObserver;

  protected Thread thread;

  protected String baseUrl;
  protected String workerId;
  protected int maxTasks;
  protected boolean usePriority;

  // protected TypedValues typedValues;

  protected long clientLockDuration;

  public TopicSubscriptionManager(String workerId, int maxTasks, String baseUrl, boolean usePriority, long clientLockDuration) {
    this.subscriptions = new CopyOnWriteArrayList<>();
    this.taskTopicRequests = new ArrayList<>();
    this.externalTaskHandlers = new HashMap<>();

    this.semaphore = new Semaphore(0);

    this.channel = ManagedChannelBuilder.forTarget(baseUrl).usePlaintext().build();
    this.stub = ExternalTaskGrpc.newStub(channel);
    this.service = new ExternalTaskServiceImpl(stub);

    this.workerId = workerId;
    this.maxTasks = maxTasks;
    this.usePriority = usePriority;
    this.baseUrl = baseUrl;
    this.clientLockDuration = clientLockDuration;
    // this.typedValues = typedValues;
  }

  public void run() {
    while (isRunning.get()) {
      try {
        if (!taskTopicRequests.isEmpty()) {
          requestObserver.onNext(buildRequest());
          try {
            semaphore.acquire();
          } catch (InterruptedException e) {
            LOG.logInfo("NA", "Client was stopped while waiting on answer from server", e);
          }
        }
      } catch (Throwable e) {
        LOG.exceptionWhileAcquiringTasks(e);
      }
    }
    requestObserver.onCompleted();
  }

  protected void prepareTopics() {
    taskTopicRequests.clear();
    externalTaskHandlers.clear();
    subscriptions.forEach(this::prepareAcquisition);
  }

  protected void prepareAcquisition(TopicSubscription subscription) {
    FetchExternalTaskTopic taskTopicRequest = fromTopicSubscription(subscription, clientLockDuration);
    taskTopicRequests.add(taskTopicRequest);

    String topicName = subscription.getTopicName();
    ExternalTaskHandler externalTaskHandler = subscription.getExternalTaskHandler();
    externalTaskHandlers.put(topicName, externalTaskHandler);
  }

  protected static FetchExternalTaskTopic fromTopicSubscription(TopicSubscription topicSubscription, long clientLockDuration) {
    Long lockDuration = topicSubscription.getLockDuration();

    if (lockDuration == null) {
      lockDuration = clientLockDuration;
    }

    String topicName = topicSubscription.getTopicName();
    // List<String> variables = topicSubscription.getVariableNames();

    FetchExternalTaskTopic.Builder topicRequestDto = FetchExternalTaskTopic.newBuilder()
        .setTopicName(topicName)
        .setLockDuration(lockDuration);
    if (notEmpty(topicSubscription.getBusinessKey())) {
      topicRequestDto.setBusinessKey(topicSubscription.getBusinessKey());
    }
    if (notEmpty(topicSubscription.getProcessDefinitionId())) {
      topicRequestDto.setProcessDefinitionId(topicSubscription.getProcessDefinitionId());
    }
    if (notEmpty(topicSubscription.getProcessDefinitionIdIn())) {
      topicRequestDto.addAllProcessDefinitionIdIn(topicSubscription.getProcessDefinitionIdIn());
    }
    if (notEmpty(topicSubscription.getProcessDefinitionKey())) {
      topicRequestDto.setProcessDefinitionKey(topicSubscription.getProcessDefinitionKey());
    }
    if (notEmpty(topicSubscription.getProcessDefinitionKeyIn())) {
      topicRequestDto.addAllProcessDefinitionKeyIn(topicSubscription.getProcessDefinitionKeyIn());
    }
    if (topicSubscription.isWithoutTenantId()) {
      topicRequestDto.setWithoutTenantId(topicSubscription.isWithoutTenantId());
    }
    if (notEmpty(topicSubscription.getTenantIdIn())) {
      topicRequestDto.addAllTenantIdIn(topicSubscription.getTenantIdIn());
    }
    if (notEmpty(topicSubscription.getProcessDefinitionVersionTag())) {
      topicRequestDto.setProcessDefinitionVersionTag(topicSubscription.getProcessDefinitionVersionTag());
    }
    return topicRequestDto.build();
  }

  public synchronized void stop() {
    if (isRunning.compareAndSet(true, false)) {
      try {
        thread.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOG.exceptionWhileShuttingDown(e);
      }
    }
  }

  public synchronized void start() {
    if (isRunning.compareAndSet(false, true)) {
      prepareTopics();
      initRequestObserver();
      thread = new Thread(this, TopicSubscriptionManager.class.getSimpleName());
      thread.start();
    }
  }

  protected void initRequestObserver() {
    requestObserver = stub.fetchAndLock(new StreamObserver<FetchAndLockResponse>() {

      @Override
      public void onNext(FetchAndLockResponse reply) {
        ExternalTaskHandler taskHandler = externalTaskHandlers.get(reply.getTopicName());

        if (taskHandler != null) {
          try {
            taskHandler.handleTask(reply, service);
          } catch (Exception e) {
            LOG.exceptionWhileExecutingExternalTaskHandler(reply.getTopicName(), e);
          }
        } else {
          LOG.taskHandlerIsNull(reply.getTopicName());
        }

        semaphore.release();
      }

      @Override
      public void onError(Throwable var1) {
        LOG.logError("NA", "Oh oh, error on sever", var1);
      }

      @Override
      public void onCompleted() {
        LOG.logInfo("NA", "Server is done", null);
      }
    });
  }

  protected void subscribe(TopicSubscription subscription) {
    if (!subscriptions.addIfAbsent(subscription)) {
      String topicName = subscription.getTopicName();
      throw LOG.topicNameAlreadySubscribedException(topicName);
    }
  }

  protected void unsubscribe(TopicSubscription subscription) {
    subscriptions.remove(subscription);
    prepareTopics();
  }

  protected FetchAndLockRequest buildRequest() {
    return FetchAndLockRequest.newBuilder()
        .setWorkerId(workerId)
        .setUsePriority(usePriority)
        .addAllTopic(taskTopicRequests)
        .build();
  }

  public List<TopicSubscription> getSubscriptions() {
    return subscriptions;
  }

  public boolean isRunning() {
    return isRunning.get();
  }

  private static boolean notEmpty(String value) {
    return value != null && !value.isEmpty();
  }

  private static boolean notEmpty(Collection<?> list) {
    return list != null && !list.isEmpty();
  }

}
