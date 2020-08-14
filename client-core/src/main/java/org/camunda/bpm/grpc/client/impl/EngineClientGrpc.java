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
package org.camunda.bpm.grpc.client.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import org.camunda.bpm.client.impl.EngineClient;
import org.camunda.bpm.client.impl.EngineClientException;
import org.camunda.bpm.client.variable.impl.TypedValueField;
import org.camunda.bpm.grpc.CompleteRequest;
import org.camunda.bpm.grpc.ExtendLockRequest;
import org.camunda.bpm.grpc.ExternalTaskGrpc;
import org.camunda.bpm.grpc.ExternalTaskGrpc.ExternalTaskBlockingStub;
import org.camunda.bpm.grpc.ExternalTaskGrpc.ExternalTaskStub;
import org.camunda.bpm.grpc.FetchAndLockRequest;
import org.camunda.bpm.grpc.FetchAndLockResponse;
import org.camunda.bpm.grpc.GetBinaryVariableRequest;
import org.camunda.bpm.grpc.GetBinaryVariableResponse;
import org.camunda.bpm.grpc.HandleBpmnErrorRequest;
import org.camunda.bpm.grpc.HandleFailureRequest;
import org.camunda.bpm.grpc.TypedValueFieldDto;
import org.camunda.bpm.grpc.UnlockRequest;
import org.camunda.bpm.grpc.core.VariableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

public class EngineClientGrpc extends EngineClient {

  private static final Logger LOG = LoggerFactory.getLogger(EngineClientGrpc.class);

  protected ManagedChannel channel;
  protected ExternalTaskStub stub;
  protected ExternalTaskBlockingStub blockingStub;

  public EngineClientGrpc(String workerId, int maxTasks, Long asyncResponseTimeout, String baseUrl) {
    this(workerId, maxTasks, asyncResponseTimeout, baseUrl, true);
  }

  public EngineClientGrpc(String workerId, int maxTasks, Long asyncResponseTimeout, String baseUrl, boolean usePriority) {
    super(workerId, maxTasks, asyncResponseTimeout, baseUrl, null, usePriority);
    this.channel = ManagedChannelBuilder.forTarget(baseUrl).usePlaintext().build();
    this.stub = ExternalTaskGrpc.newStub(channel);
    this.blockingStub = ExternalTaskGrpc.newBlockingStub(channel);
  }

  public StreamObserver<FetchAndLockRequest> fetchAndLock(StreamObserver<FetchAndLockResponse> responseObserver) {
    return stub.fetchAndLock(responseObserver);
  }

  @Override
  public void unlock(String taskId) throws EngineClientException {
    UnlockRequest request = UnlockRequest.newBuilder()
        .setId(taskId)
        .build();

    stub.unlock(request, createLoggingObserver(
        response -> "Task "+ request.getId() + " unlocked with status " + response.getStatus(),
        "Could not unlock the task " + request.getId() + " (server error)"));
  }

  @Override
  public void complete(String taskId, Map<String, Object> variables, Map<String, Object> localVariables) throws EngineClientException {
    Map<String, TypedValueFieldDto> typedValueDtoMap = toTypedValueFields(typedValues.serializeVariables(variables));
    Map<String, TypedValueFieldDto> localTypedValueDtoMap = toTypedValueFields(typedValues.serializeVariables(localVariables));

    CompleteRequest request = CompleteRequest.newBuilder()
        .setWorkerId(workerId)
        .setId(taskId)
        .putAllLocalVariables(localTypedValueDtoMap)
        .putAllVariables(typedValueDtoMap)
        .build();

    stub.complete(request, createLoggingObserver(
        response -> "Task " + request.getId() + " completed with status " + response.getStatus(),
        "Could not complete the task " + request.getId() + " (server error)"));
  }

  @Override
  public void failure(String taskId, String errorMessage, String errorDetails, int retries, long retryTimeout) throws EngineClientException {
    HandleFailureRequest request = HandleFailureRequest.newBuilder()
        .setId(taskId)
        .setErrorMessage(errorMessage)
        .setErrorDetails(errorDetails)
        .setRetries(retries)
        .setRetryTimeout(retryTimeout)
        .build();

    stub.handleFailure(request, createLoggingObserver(
        response -> "Failure for Task " + request.getId() + " handled with status " + response.getStatus(),
        "Could not handle the failure for the task " + request.getId() + " (server error)"));
  }

  @Override
  public void bpmnError(String taskId, String errorCode, String errorMessage, Map<String, Object> variables) throws EngineClientException {
    // TODO add variables to proto
//    Map<String, TypedValueField> typeValueDtoMap = typedValues.serializeVariables(variables);

    HandleBpmnErrorRequest request = HandleBpmnErrorRequest.newBuilder()
        .setId(taskId)
        .setErrorCode(errorCode)
        .setErrorMessage(errorMessage)
//        .setVariables(typeValueDtoMap)
        .build();

    stub.handleBpmnError(request, createLoggingObserver(
        response -> "BPMN Error for Task " + request.getId() + " handled with status " + response.getStatus(),
        "Could not handle the BPMN Error for the task " + request.getId() + " (server error)"));
  }

  @Override
  public void extendLock(String taskId, long newDuration) throws EngineClientException {
    ExtendLockRequest request = ExtendLockRequest.newBuilder()
        .setId(taskId)
        .setDuration(newDuration)
        .build();

    stub.extendLock(request, createLoggingObserver(
        response -> "Lock for Task " + request.getId() + " extended with status " + response.getStatus(),
        "Could not extend the lock for the task " + request.getId() + " (server error)"));
  }

  @Override
  public byte[] getLocalBinaryVariable(String variableName, String processInstanceId) throws EngineClientException {
    GetBinaryVariableRequest request = GetBinaryVariableRequest.newBuilder()
        .setProcessInstanceId(processInstanceId)
        .setVariableName(variableName)
        .build();

    GetBinaryVariableResponse localBinaryVariable = blockingStub.getLocalBinaryVariable(request);
    if (localBinaryVariable == null || localBinaryVariable.getData() == null || localBinaryVariable.getData().isEmpty()) {
      return null;
    }
    return localBinaryVariable.getData().toByteArray();
  }

  protected static <T> StreamObserver<T> createLoggingObserver(Function<T, String> succesMessageFunction, String errorMessage) {
    return new StreamObserver<T>() {
      @Override
      public void onNext(T response) {
        LOG.info(succesMessageFunction.apply(response));
      }

      @Override
      public void onError(Throwable throwable) {
        LOG.error(errorMessage, throwable);
      }

      @Override
      public void onCompleted() {
        // nothing to do
      }
    };
  }

  protected static Map<String, TypedValueFieldDto> toTypedValueFields(Map<String, TypedValueField> variablesMap) {
    Map<String, TypedValueFieldDto> map = new HashMap<>();
    for (Entry<String, TypedValueField> entry : variablesMap.entrySet()) {
      TypedValueFieldDto.Builder field = TypedValueFieldDto.newBuilder();
      field.setType(entry.getValue().getType());
      field.setValue(VariableUtils.pack(entry.getValue().getValue()));
      field.putAllValueInfo(VariableUtils.packMap(entry.getValue().getValueInfo()));
      map.put(entry.getKey(), field.build());
    }
    return map;
  }

}
