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

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import org.camunda.bpm.client.impl.EngineClient;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.impl.ExternalTaskImpl;
import org.camunda.bpm.client.topic.TopicSubscription;
import org.camunda.bpm.client.topic.impl.TopicSubscriptionImpl;
import org.camunda.bpm.client.topic.impl.TopicSubscriptionManager;
import org.camunda.bpm.client.topic.impl.dto.TopicRequestDto;
import org.camunda.bpm.client.variable.impl.TypedValues;
import org.camunda.bpm.grpc.FetchAndLockRequest;
import org.camunda.bpm.grpc.FetchAndLockRequest.FetchExternalTaskTopic;
import org.camunda.bpm.grpc.client.impl.EngineClientGrpc;

import com.google.protobuf.Timestamp;

import org.camunda.bpm.grpc.FetchAndLockResponse;

import io.grpc.stub.StreamObserver;

public class TopicSubscriptionManagerGrpc extends TopicSubscriptionManager {

  private StreamObserver<FetchAndLockRequest> requestObserver;

  private Semaphore semaphore;

  public TopicSubscriptionManagerGrpc(EngineClient engineClient, TypedValues typedValues, long clientLockDuration) {
    super(engineClient, typedValues, clientLockDuration);
    this.semaphore = new Semaphore(0);
  }

  @Override
  public void run() {
    super.run();
    requestObserver.onCompleted();
  }

  @Override
  protected void acquire() {
    if (!taskTopicRequests.isEmpty()) {
      requestObserver.onNext(buildRequest());
      try {
        semaphore.acquire();
      } catch (InterruptedException e) {
        LOG.logInfo("NA", "Client was stopped while waiting on answer from server", e);
      }
    }
  }

  protected void prepareTopics() {
    taskTopicRequests.clear();
    externalTaskHandlers.clear();
    subscriptions.forEach(this::prepareAcquisition);
  }

  public synchronized void start() {
    if (isRunning.compareAndSet(false, true)) {
      prepareTopics();
      initRequestObserver();
      thread = new Thread(this, TopicSubscriptionManagerGrpc.class.getSimpleName());
      thread.start();
    }
  }

  protected void initRequestObserver() {
    requestObserver = ((EngineClientGrpc) engineClient).fetchAndLock(new StreamObserver<FetchAndLockResponse>() {

      @Override
      public void onNext(FetchAndLockResponse reply) {
        ExternalTaskHandler taskHandler = externalTaskHandlers.get(reply.getTopicName());

        if (taskHandler != null) {
          handleExternalTask(to(reply), taskHandler);
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
    super.subscribe(subscription);
    prepareTopics();
  }

  protected void unsubscribe(TopicSubscriptionImpl subscription) {
    super.unsubscribe(subscription);
    prepareTopics();
  }

  protected FetchAndLockRequest buildRequest() {
    return FetchAndLockRequest.newBuilder()
        .setWorkerId(engineClient.getWorkerId())
        .setUsePriority(engineClient.isUsePriority())
        .addAllTopic(from(taskTopicRequests)).build();
  }

  private Iterable<? extends FetchExternalTaskTopic> from(List<TopicRequestDto> taskTopicRequests) {
    return taskTopicRequests.stream().map(TopicSubscriptionManagerGrpc::from).collect(Collectors.toList());
  }

  protected static FetchExternalTaskTopic from(TopicRequestDto topicSubscription) {
    FetchExternalTaskTopic.Builder topicRequestDto = FetchExternalTaskTopic.newBuilder().setTopicName(topicSubscription.getTopicName())
        .setLockDuration(topicSubscription.getLockDuration());
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
    // TODO add "variables" to proto
//    if (notEmpty(topicSubscription.getVariables())) {
//      topicRequestDto.setVariables(topicSubscription.getVariables());
//    }
    topicRequestDto.setLocalVariables(topicSubscription.isLocalVariables());
    topicRequestDto.setIncludeExtensionProperties(topicSubscription.isIncludeExtensionProperties());
    return topicRequestDto.build();
  }

  protected static ExternalTask to(FetchAndLockResponse response) {
    // TODO add "variables" to proto
    ExternalTaskImpl task = new ExternalTaskImpl();
    task.setActivityId(response.getActivityId());
    task.setActivityInstanceId(response.getActivityInstanceId());
    task.setErrorMessage(response.getErrorMessage());
    task.setErrorDetails(response.getErrorDetails());
    task.setExecutionId(response.getExecutionId());
    task.setId(response.getId());
    task.setLockExpirationTime(getDate(response.getLockExpirationTime()));
    task.setProcessDefinitionId(response.getProcessDefinitionId());
    task.setProcessDefinitionKey(response.getProcessDefinitionKey());
    task.setProcessDefinitionVersionTag(response.getProcessDefinitionVersionTag());
    task.setProcessInstanceId(response.getProcessInstanceId());
    task.setRetries(response.getRetries());
    task.setWorkerId(response.getWorkerId());
    task.setTopicName(response.getTopicName());
    task.setTenantId(response.getTenantId());
    task.setPriority(response.getPriority());
//    task.setVariables(response.getVariables());
    task.setBusinessKey(response.getBusinessKey());
    task.setExtensionProperties(response.getExtensionPropertiesMap());
    return task;
  }

  private static Date getDate(Timestamp ts) {
    return Date.from(Instant
      .ofEpochSecond(ts.getSeconds() , ts.getNanos())
      .atZone(ZoneId.systemDefault())
      .toInstant());
  }

  private static boolean notEmpty(String value) {
    return value != null && !value.isEmpty();
  }

  private static boolean notEmpty(Collection<?> list) {
    return list != null && !list.isEmpty();
  }

}
