package org.camunda.bpm.grpc.client;

import org.camunda.bpm.grpc.ExternalTaskGrpc;
import org.camunda.bpm.grpc.FetchAndLockReply;

public interface ExternalTaskHandler {

  void handleTask(FetchAndLockReply reply, ExternalTaskGrpc.ExternalTaskStub service);

}
