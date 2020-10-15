package org.camunda.bpm.spring.boot.starter.grpc.externaltask;

import java.net.HttpURLConnection;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.exception.NotFoundException;
import org.camunda.bpm.engine.externaltask.ExternalTaskQueryBuilder;
import org.camunda.bpm.engine.externaltask.ExternalTaskQueryTopicBuilder;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.impl.type.PrimitiveValueTypeImpl.DateTypeImpl;
import org.camunda.bpm.engine.variable.type.FileValueType;
import org.camunda.bpm.engine.variable.type.PrimitiveValueType;
import org.camunda.bpm.engine.variable.type.SerializableValueType;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.type.ValueTypeResolver;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.camunda.bpm.grpc.CompleteRequest;
import org.camunda.bpm.grpc.CompleteResponse;
import org.camunda.bpm.grpc.ExtendLockRequest;
import org.camunda.bpm.grpc.ExtendLockResponse;
import org.camunda.bpm.grpc.ExternalTaskGrpc.ExternalTaskImplBase;
import org.camunda.bpm.grpc.FetchAndLockRequest;
import org.camunda.bpm.grpc.FetchAndLockRequest.FetchExternalTaskTopic;
import org.camunda.bpm.grpc.FetchAndLockResponse;
import org.camunda.bpm.grpc.HandleBpmnErrorRequest;
import org.camunda.bpm.grpc.HandleBpmnErrorResponse;
import org.camunda.bpm.grpc.HandleFailureRequest;
import org.camunda.bpm.grpc.HandleFailureResponse;
import org.camunda.bpm.grpc.TypedValueFieldDto;
import org.camunda.bpm.grpc.UnlockRequest;
import org.camunda.bpm.grpc.UnlockResponse;
import org.camunda.bpm.grpc.core.VariableUtils;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.beans.factory.annotation.Autowired;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@GRpcService
public class ExternalTaskServiceGrpc extends ExternalTaskImplBase {

  @Autowired
  private ExternalTaskService externalTaskService;

  @Autowired
  private ProcessEngine processEngine;

  @Autowired
  private WaitingClientInformer informer;

  @Override
  public StreamObserver<FetchAndLockRequest> fetchAndLock(StreamObserver<FetchAndLockResponse> responseObserver) {
    StreamObserver<FetchAndLockRequest> requestObserver = new StreamObserver<FetchAndLockRequest>() {

      @Override
      public void onNext(FetchAndLockRequest request) {
        informClient(request, responseObserver);
      }

      @Override
      public void onError(Throwable t) {
        if (Status.CANCELLED.getCode().equals(Status.fromThrowable(t).getCode())) {
          log.info("Client disconnected, removing pending request");
          informer.removeClientRequests(responseObserver);
        } else {
          log.error("Server received error", t);
        }
      }

      @Override
      public void onCompleted() {
        informer.removeClientRequests(responseObserver);
        responseObserver.onCompleted();
      }
    };
    return requestObserver;
  }

  @Override
  public void complete(CompleteRequest request, StreamObserver<CompleteResponse> responseObserver) {
    try {
      VariableMap variables = fromTypedValueFields(request.getVariablesMap());
      VariableMap localVariables = fromTypedValueFields(request.getLocalVariablesMap());
      externalTaskService.complete(request.getId(), request.getWorkerId(), variables, localVariables);
      responseObserver.onNext(CompleteResponse.newBuilder().setStatus(HttpURLConnection.HTTP_NO_CONTENT).build());
      responseObserver.onCompleted();
    } catch (NotFoundException nfe) {
      log.debug("Task with id {} not found", request.getId());
      responseObserver.onError(createStatusRuntimeException(Status.NOT_FOUND, nfe));
    } catch (BadUserRequestException bure) {
      log.debug("Task with id {} not locked by worker {}", request.getId(), request.getWorkerId());
      responseObserver.onError(createStatusRuntimeException(Status.PERMISSION_DENIED, bure));
    } catch (Exception e) {
      log.error("Error on completing task " + request.getId(), e);
      responseObserver.onError(createStatusRuntimeException(Status.INTERNAL, e));
    }
  }

  @Override
  public void handleFailure(HandleFailureRequest request, StreamObserver<HandleFailureResponse> responseObserver) {
    try {
      externalTaskService.handleFailure(request.getId(), request.getWorkerId(), request.getErrorMessage(), request.getErrorDetails(), request.getRetries(),
          request.getRetryTimeout());
      responseObserver.onNext(HandleFailureResponse.newBuilder().setStatus(HttpURLConnection.HTTP_NO_CONTENT).build());
      responseObserver.onCompleted();
    } catch (NotFoundException nfe) {
      log.debug("Task with id {} not found", request.getId());
      responseObserver.onError(createStatusRuntimeException(Status.NOT_FOUND, nfe));
    } catch (BadUserRequestException bure) {
      log.debug("Task with id {} not locked by worker {}", request.getId(), request.getWorkerId());
      responseObserver.onError(createStatusRuntimeException(Status.PERMISSION_DENIED, bure));
    } catch (Exception e) {
      log.error("Error on handling failure for task " + request.getId(), e);
      responseObserver.onError(e);
    }
  }

  @Override
  public void handleBpmnError(HandleBpmnErrorRequest request, StreamObserver<HandleBpmnErrorResponse> responseObserver) {
    try {
      externalTaskService.handleBpmnError(request.getId(), request.getWorkerId(), request.getErrorCode(), request.getErrorMessage());
      responseObserver.onNext(HandleBpmnErrorResponse.newBuilder().setStatus(HttpURLConnection.HTTP_NO_CONTENT).build());
      responseObserver.onCompleted();
    } catch (NotFoundException nfe) {
      log.debug("Task with id {} not found", request.getId());
      responseObserver.onError(createStatusRuntimeException(Status.NOT_FOUND, nfe));
    } catch (BadUserRequestException bure) {
      log.debug("Task with id {} not locked by worker {}", request.getId(), request.getWorkerId());
      responseObserver.onError(createStatusRuntimeException(Status.PERMISSION_DENIED, bure));
    } catch (Exception e) {
      log.error("Error on handling BPMN error for task " + request.getId(), e);
      responseObserver.onError(e);
    }
  }

  @Override
  public void unlock(UnlockRequest request, StreamObserver<UnlockResponse> responseObserver) {
    try {
      externalTaskService.unlock(request.getId());
      responseObserver.onNext(UnlockResponse.newBuilder().setStatus(HttpURLConnection.HTTP_NO_CONTENT).build());
      responseObserver.onCompleted();
    } catch (NotFoundException nfe) {
      log.debug("Task with id {} not found", request.getId());
      responseObserver.onError(createStatusRuntimeException(Status.NOT_FOUND, nfe));
    } catch (Exception e) {
      log.error("Error on unlocking task " + request.getId(), e);
      responseObserver.onError(e);
    }
  }

  @Override
  public void extendLock(ExtendLockRequest request, StreamObserver<ExtendLockResponse> responseObserver) {
    try {
      externalTaskService.extendLock(request.getId(), request.getWorkerId(), request.getDuration());
      responseObserver.onNext(ExtendLockResponse.newBuilder().setStatus(HttpURLConnection.HTTP_NO_CONTENT).build());
      responseObserver.onCompleted();
    } catch (NotFoundException nfe) {
      log.debug("Task with id {} not found", request.getId());
      responseObserver.onError(createStatusRuntimeException(Status.NOT_FOUND, nfe));
    } catch (BadUserRequestException bure) {
      log.debug("Task with id {} not locked by worker {}", request.getId(), request.getWorkerId());
      responseObserver.onError(createStatusRuntimeException(Status.PERMISSION_DENIED, bure));
    } catch (Exception e) {
      log.error("Error on extending lock for task " + request.getId(), e);
      responseObserver.onError(e);
    }
  }

  protected static StatusRuntimeException createStatusRuntimeException(Status status, Exception e) {
    return status.withDescription(e.getMessage()).withCause(e).asRuntimeException();
  }

  protected void informClient(FetchAndLockRequest request, StreamObserver<FetchAndLockResponse> client) {
    ExternalTaskQueryBuilder fetchBuilder = createQuery(request);
    List<LockedExternalTask> lockedTasks = fetchBuilder.execute();
    if (lockedTasks.isEmpty()) {
      // if no external tasks locked => save the response observer and
      // notify later when external task created for the topic in the engine
      informer.addWaitingClient(request, client);
    } else {
      FetchAndLockResponse reply = fromLockedTask(request, lockedTasks);
      client.onNext(reply);
    }
  }

  protected FetchAndLockResponse fromLockedTask(FetchAndLockRequest request, List<LockedExternalTask> lockedTasks) {
    return FetchAndLockResponse.newBuilder()
      .setId(VariableUtils.getSafe(lockedTasks.get(0).getId()))
      .setWorkerId(VariableUtils.getSafe(request.getWorkerId()))
      .setTopicName(VariableUtils.getSafe(lockedTasks.get(0).getTopicName()))
      .setLockExpirationTime(VariableUtils.getTimestamp(lockedTasks.get(0).getLockExpirationTime()))
      .setRetries(VariableUtils.getSafe(lockedTasks.get(0).getRetries()))
      .setErrorMessage(VariableUtils.getSafe(lockedTasks.get(0).getErrorMessage()))
      .setErrorDetails(VariableUtils.getSafe(lockedTasks.get(0).getErrorDetails()))
      .setProcessInstanceId(VariableUtils.getSafe(lockedTasks.get(0).getProcessInstanceId()))
      .setExecutionId(VariableUtils.getSafe(lockedTasks.get(0).getExecutionId()))
      .setActivityId(VariableUtils.getSafe(lockedTasks.get(0).getActivityId()))
      .setActivityInstanceId(VariableUtils.getSafe(lockedTasks.get(0).getActivityInstanceId()))
      .setProcessDefinitionId(VariableUtils.getSafe(lockedTasks.get(0).getProcessDefinitionId()))
      .setProcessDefinitionKey(VariableUtils.getSafe(lockedTasks.get(0).getProcessDefinitionKey()))
      .setProcessDefinitionVersionTag(VariableUtils.getSafe(lockedTasks.get(0).getProcessDefinitionVersionTag()))
      .setTenantId(VariableUtils.getSafe(lockedTasks.get(0).getTenantId()))
      .setPriority(lockedTasks.get(0).getPriority())
      .setBusinessKey(VariableUtils.getSafe(lockedTasks.get(0).getBusinessKey()))
      .putAllExtensionProperties(lockedTasks.get(0).getExtensionProperties())
      .putAllVariables(VariableUtils.toTypedValueFields(lockedTasks.get(0).getVariables()))
      .build();
  }

  protected VariableMap fromTypedValueFields(Map<String, TypedValueFieldDto> variablesMap) {
    VariableMap map = Variables.createVariables();
    for (Entry<String, TypedValueFieldDto> entry : variablesMap.entrySet()) {
      Object value = VariableUtils.unpack(entry.getValue().getValue());
      Map<String, Object> valueInfo = VariableUtils.unpackMap(entry.getValue().getValueInfoMap());
      map.putValueTyped(entry.getKey(), toTypedValue(entry.getValue().getType(), value, valueInfo));
    }
    return map;
  }

  protected TypedValue toTypedValue(String type, Object value, Map<String, Object> valueInfo) {
    ValueTypeResolver valueTypeResolver = processEngine.getProcessEngineConfiguration().getValueTypeResolver();

    if (type == null) {
      if (valueInfo != null && valueInfo.get(ValueType.VALUE_INFO_TRANSIENT) instanceof Boolean) {
        return Variables.untypedValue(value, (Boolean) valueInfo.get(ValueType.VALUE_INFO_TRANSIENT));
      }
      return Variables.untypedValue(value);
    }

    ValueType valueType = valueTypeResolver.typeForName(StringUtils.uncapitalize(type));
    if (valueType == null) {
      throw new IllegalArgumentException("Unsupported value type '" + type + "'");
    } else {
      if (valueType instanceof PrimitiveValueType) {
        PrimitiveValueType primitiveValueType = (PrimitiveValueType) valueType;
        Class<?> javaType = primitiveValueType.getJavaType();
        Object mappedValue = null;
        if (value != null) {
          if (javaType.isAssignableFrom(value.getClass())) {
            mappedValue = value;
          } else if (valueType instanceof DateTypeImpl) {
            mappedValue = Date.from(ZonedDateTime.parse((String) value, VariableUtils.DATETIME_FORMATTER).toInstant());
          } else {
            throw new IllegalArgumentException(String.format("Cannot convert value '%s' of type '%s' to java type %s", value, type, javaType.getName()));
          }
        }
        return valueType.createValue(mappedValue, valueInfo);
      } else if (valueType instanceof SerializableValueType) {
        if (value != null && !(value instanceof String)) {
          throw new IllegalArgumentException("Must provide 'null' or String value for value of SerializableValue type '" + type + "'.");
        }
        return ((SerializableValueType) valueType).createValueFromSerialized((String) value, valueInfo);
      } else if (valueType instanceof FileValueType) {
        if (value instanceof String) {
          value = Base64.getDecoder().decode((String) value);
        }
        return valueType.createValue(value, valueInfo);
      } else {
        return valueType.createValue(value, valueInfo);
      }
    }
  }

  protected ExternalTaskQueryBuilder createQuery(FetchAndLockRequest request) {
    ExternalTaskQueryBuilder fetchBuilder = externalTaskService.fetchAndLock(1,
        request.getWorkerId(),
        request.getUsePriority());

      if (request.getTopicList() != null) {
        for (FetchExternalTaskTopic topicDto : request.getTopicList()) {
          ExternalTaskQueryTopicBuilder topicFetchBuilder = fetchBuilder.topic(topicDto.getTopicName(), topicDto.getLockDuration());

          if (VariableUtils.notEmpty(topicDto.getBusinessKey())) {
            topicFetchBuilder.businessKey(topicDto.getBusinessKey());
          }

          if (VariableUtils.notEmpty(topicDto.getProcessDefinitionId())) {
            topicFetchBuilder.processDefinitionId(topicDto.getProcessDefinitionId());
          }

          if (VariableUtils.notEmpty(topicDto.getProcessDefinitionIdInList())) {
            topicFetchBuilder.processDefinitionIdIn(topicDto.getProcessDefinitionIdInList().toArray(new String[topicDto.getProcessDefinitionIdInList().size()]));
          }

          if (VariableUtils.notEmpty(topicDto.getProcessDefinitionKey())) {
            topicFetchBuilder.processDefinitionKey(topicDto.getProcessDefinitionKey());
          }

          if (VariableUtils.notEmpty(topicDto.getProcessDefinitionKeyInList())) {
            topicFetchBuilder.processDefinitionKeyIn(topicDto.getProcessDefinitionKeyInList().toArray(new String[topicDto.getProcessDefinitionKeyInList().size()]));
          }

          if (VariableUtils.notEmpty((topicDto.getVariablesList()))) {
            topicFetchBuilder.variables(topicDto.getVariablesList());
          }

          if (topicDto.getDeserializeValues()) {
            topicFetchBuilder.enableCustomObjectDeserialization();
          }

          if (topicDto.getLocalVariables()) {
            topicFetchBuilder.localVariables();
          }

          if (topicDto.getWithoutTenantId()) {
            topicFetchBuilder.withoutTenantId();
          }

          if (VariableUtils.notEmpty(topicDto.getTenantIdInList())) {
            topicFetchBuilder.tenantIdIn(topicDto.getTenantIdInList().toArray(new String[topicDto.getTenantIdInList().size()]));
          }

          if(VariableUtils.notEmpty(topicDto.getProcessDefinitionVersionTag())) {
            topicFetchBuilder.processDefinitionVersionTag(topicDto.getProcessDefinitionVersionTag());
          }

          fetchBuilder = topicFetchBuilder;
        }
      }

      return fetchBuilder;
  }

}
