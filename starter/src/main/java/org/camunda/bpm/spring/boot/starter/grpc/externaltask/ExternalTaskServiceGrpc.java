package org.camunda.bpm.spring.boot.starter.grpc.externaltask;

import static java.lang.Boolean.TRUE;

import java.util.Collection;
import java.util.List;

import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.externaltask.ExternalTaskQueryBuilder;
import org.camunda.bpm.engine.externaltask.ExternalTaskQueryTopicBuilder;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.camunda.bpm.grpc.CompleteRequest;
import org.camunda.bpm.grpc.CompleteResponse;
import org.camunda.bpm.grpc.ExtendLockRequest;
import org.camunda.bpm.grpc.ExtendLockResponse;
import org.camunda.bpm.grpc.ExternalTaskGrpc.ExternalTaskImplBase;
import org.camunda.bpm.grpc.FetchAndLockResponse;
import org.camunda.bpm.grpc.HandleBpmnErrorRequest;
import org.camunda.bpm.grpc.HandleBpmnErrorResponse;
import org.camunda.bpm.grpc.HandleFailureRequest;
import org.camunda.bpm.grpc.HandleFailureResponse;
import org.camunda.bpm.grpc.UnlockRequest;
import org.camunda.bpm.grpc.UnlockResponse;
import org.camunda.bpm.grpc.FetchAndLockRequest;
import org.camunda.bpm.grpc.FetchAndLockRequest.FetchExternalTaskTopic;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.beans.factory.annotation.Autowired;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@GRpcService
public class ExternalTaskServiceGrpc extends ExternalTaskImplBase {

  @Autowired
  private ExternalTaskService externalTaskService;

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
        log.error("uh oh, server received error", t);
        if (Status.CANCELLED.equals(Status.fromThrowable(t))) {
          informer.removeClientRequests(responseObserver);
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
      externalTaskService.complete(request.getId(), request.getWorkerId());
      responseObserver.onNext(CompleteResponse.newBuilder().setStatus("200").build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      log.error("Error on completing task " + request.getId(), e);
      responseObserver.onError(e);
    }
  }

  @Override
  public void handleFailure(HandleFailureRequest request, StreamObserver<HandleFailureResponse> responseObserver) {
    try {
      externalTaskService.handleFailure(request.getId(), request.getWorkerId(), request.getErrorMessage(), request.getErrorDetails(), request.getRetries(),
          request.getRetryTimeout());
      responseObserver.onNext(HandleFailureResponse.newBuilder().setStatus("200").build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      log.error("Error on handling failure for task " + request.getId(), e);
      responseObserver.onError(e);
    }
  }

  @Override
  public void handleBpmnError(HandleBpmnErrorRequest request, StreamObserver<HandleBpmnErrorResponse> responseObserver) {
    try {
      externalTaskService.handleBpmnError(request.getId(), request.getWorkerId(), request.getErrorCode(), request.getErrorMessage());
      responseObserver.onNext(HandleBpmnErrorResponse.newBuilder().setStatus("200").build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      log.error("Error on handling BPMN error for task " + request.getId(), e);
      responseObserver.onError(e);
    }
  }

  @Override
  public void unlock(UnlockRequest request, StreamObserver<UnlockResponse> responseObserver) {
    try {
      externalTaskService.unlock(request.getId());
      responseObserver.onNext(UnlockResponse.newBuilder().setStatus("200").build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      log.error("Error on unlocking task " + request.getId(), e);
      responseObserver.onError(e);
    }
  }

  @Override
  public void extendLock(ExtendLockRequest request, StreamObserver<ExtendLockResponse> responseObserver) {
    try {
      externalTaskService.extendLock(request.getId(), request.getWorkerId(), request.getDuration());
      responseObserver.onNext(ExtendLockResponse.newBuilder().setStatus("200").build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      log.error("Error on extending lock for task " + request.getId(), e);
      responseObserver.onError(e);
    }
  }

  private void informClient(FetchAndLockRequest request, StreamObserver<FetchAndLockResponse> client) {
    ExternalTaskQueryBuilder fetchBuilder = createQuery(request, externalTaskService);
    List<LockedExternalTask> lockedTasks = fetchBuilder.execute();
    if (lockedTasks.isEmpty()) {
      // if no external tasks locked => save the response observer and
      // notify later when external task created for the topic in the engine
      informer.addWaitingClient(request, client);
    } else {
      FetchAndLockResponse reply = FetchAndLockResponse.newBuilder()
          .setId(lockedTasks.get(0).getId())
          .setWorkerId(request.getWorkerId())
          .setTopicName(lockedTasks.get(0).getTopicName())
          .build();
      client.onNext(reply);
    }
  }

  public static ExternalTaskQueryBuilder createQuery(FetchAndLockRequest request, ExternalTaskService externalTaskService) {
    ExternalTaskQueryBuilder fetchBuilder = externalTaskService.fetchAndLock(1,
        request.getWorkerId(),
        request.getUsePriority());

      if (request.getTopicList() != null) {
        for (FetchExternalTaskTopic topicDto : request.getTopicList()) {
          ExternalTaskQueryTopicBuilder topicFetchBuilder = fetchBuilder.topic(topicDto.getTopicName(), topicDto.getLockDuration());

          if (notEmpty(topicDto.getBusinessKey())) {
            topicFetchBuilder = topicFetchBuilder.businessKey(topicDto.getBusinessKey());
          }

          if (notEmpty(topicDto.getProcessDefinitionId())) {
            topicFetchBuilder.processDefinitionId(topicDto.getProcessDefinitionId());
          }

          if (notEmpty(topicDto.getProcessDefinitionIdInList())) {
            topicFetchBuilder.processDefinitionIdIn(topicDto.getProcessDefinitionIdInList().toArray(new String[topicDto.getProcessDefinitionIdInList().size()]));
          }

          if (notEmpty(topicDto.getProcessDefinitionKey())) {
            topicFetchBuilder.processDefinitionKey(topicDto.getProcessDefinitionKey());
          }

          if (notEmpty(topicDto.getProcessDefinitionKeyInList())) {
            topicFetchBuilder.processDefinitionKeyIn(topicDto.getProcessDefinitionKeyInList().toArray(new String[topicDto.getProcessDefinitionKeyInList().size()]));
          }

          // TODO add variables
//          if (topicDto.getVariables() != null) {
//            topicFetchBuilder = topicFetchBuilder.variables(topicDto.getVariables());
//          }
//
//          if (topicDto.getProcessVariables() != null) {
//            topicFetchBuilder = topicFetchBuilder.processInstanceVariableEquals(topicDto.getProcessVariables());
//          }

          if (topicDto.getDeserializeValues()) {
            topicFetchBuilder = topicFetchBuilder.enableCustomObjectDeserialization();
          }

          if (topicDto.getLocalVariables()) {
            topicFetchBuilder = topicFetchBuilder.localVariables();
          }

          if (TRUE.equals(topicDto.getWithoutTenantId())) {
            topicFetchBuilder = topicFetchBuilder.withoutTenantId();
          }

          if (notEmpty(topicDto.getTenantIdInList())) {
            topicFetchBuilder = topicFetchBuilder.tenantIdIn(topicDto.getTenantIdInList().toArray(new String[topicDto.getTenantIdInList().size()]));
          }

          if(notEmpty(topicDto.getProcessDefinitionVersionTag())) {
            topicFetchBuilder = topicFetchBuilder.processDefinitionVersionTag(topicDto.getProcessDefinitionVersionTag());
          }

          fetchBuilder = topicFetchBuilder;
        }
      }

      return fetchBuilder;
  }

  private static boolean notEmpty(String value) {
    return value != null && !value.isEmpty();
  }

  private static boolean notEmpty(Collection<?> list) {
    return list != null && !list.isEmpty();
  }
}
