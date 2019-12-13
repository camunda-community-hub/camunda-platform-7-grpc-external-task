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
package org.camunda.bpm.grpc.client.task.impl;

import java.util.function.Function;

import org.camunda.bpm.grpc.CompleteRequest;
import org.camunda.bpm.grpc.CompleteResponse;
import org.camunda.bpm.grpc.ExtendLockRequest;
import org.camunda.bpm.grpc.ExtendLockResponse;
import org.camunda.bpm.grpc.ExternalTaskGrpc.ExternalTaskStub;
import org.camunda.bpm.grpc.FetchAndLockResponse;
import org.camunda.bpm.grpc.HandleBpmnErrorRequest;
import org.camunda.bpm.grpc.HandleBpmnErrorResponse;
import org.camunda.bpm.grpc.HandleFailureRequest;
import org.camunda.bpm.grpc.HandleFailureResponse;
import org.camunda.bpm.grpc.UnlockRequest;
import org.camunda.bpm.grpc.UnlockResponse;
import org.camunda.bpm.grpc.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.stub.StreamObserver;

/**
 *
 */
public class ExternalTaskServiceImpl implements ExternalTaskService {

  private static final Logger log = LoggerFactory.getLogger(ExternalTaskServiceImpl.class);

  private ExternalTaskStub stub;

  public ExternalTaskServiceImpl(ExternalTaskStub stub) {
    this.stub = stub;
  }

  @Override
  public void unlock(FetchAndLockResponse externalTask) {
    UnlockRequest request = UnlockRequest.newBuilder()
        .setId(externalTask.getId())
        .build();

    stub.unlock(request, this.<UnlockResponse>createLoggingObserver(
        response -> "Task "+ request.getId() + " unlocked with status " + response.getStatus(),
        "Could not unlock the task " + request.getId() + " (server error)"));
  }

  @Override
  public void complete(FetchAndLockResponse externalTask) {

    CompleteRequest request = CompleteRequest.newBuilder()
        .setWorkerId(externalTask.getWorkerId())
        .setId(externalTask.getId())
        .build();

    stub.complete(request, this.<CompleteResponse>createLoggingObserver(
        response -> "Task " + request.getId() + " completed with status " + response.getStatus(),
        "Could not complete the task " + request.getId() + " (server error)"));

  }

  @Override
  public void handleFailure(FetchAndLockResponse externalTask, String errorMessage, String errorDetails, int retries, long retryTimeout) {
    HandleFailureRequest request = HandleFailureRequest.newBuilder()
        .setId(externalTask.getId())
        .setErrorMessage(errorMessage)
        .setErrorDetails(errorDetails)
        .setRetries(retries)
        .setRetryTimeout(retryTimeout)
        .build();

    stub.handleFailure(request, this.<HandleFailureResponse>createLoggingObserver(
        response -> "Failure for Task " + request.getId() + " handled with status " + response.getStatus(),
        "Could not handle the failure for the task " + request.getId() + " (server error)"));
  }

  @Override
  public void handleBpmnError(FetchAndLockResponse externalTask, String errorCode) {
    handleBpmnError(externalTask, errorCode, null);

  }

  @Override
  public void handleBpmnError(FetchAndLockResponse externalTask, String errorCode, String errorMessage) {
    HandleBpmnErrorRequest request = HandleBpmnErrorRequest.newBuilder()
        .setId(externalTask.getId())
        .setErrorCode(errorCode)
        .setErrorMessage(errorMessage)
        .build();

    stub.handleBpmnError(request, this.<HandleBpmnErrorResponse>createLoggingObserver(
        response -> "BPMN Error for Task " + request.getId() + " handled with status " + response.getStatus(),
        "Could not handle the BPMN Error for the task " + request.getId() + " (server error)"));
  }

  @Override
  public void extendLock(FetchAndLockResponse externalTask, long newDuration) {
    ExtendLockRequest request = ExtendLockRequest.newBuilder()
        .setId(externalTask.getId())
        .setDuration(newDuration)
        .build();

    stub.extendLock(request, this.<ExtendLockResponse>createLoggingObserver(
        response -> "Lock for Task " + request.getId() + " extended with status " + response.getStatus(),
        "Could not extend the lock for the task " + request.getId() + " (server error)"));
  }

  protected <T> StreamObserver<T> createLoggingObserver(Function<T, String> succesMessageFunction, String errorMessage) {
    return new StreamObserver<T>() {
      @Override
      public void onNext(T response) {
        log.info(succesMessageFunction.apply(response));
      }

      @Override
      public void onError(Throwable throwable) {
        log.error(errorMessage, throwable);
      }

      @Override
      public void onCompleted() {
        // nothing to do
      }
    };
  }
}
