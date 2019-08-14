package org.camunda.bpm.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExternalTaskClientGrpc {
  public static void main(String[] args) {

    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565).usePlaintext()
            .build();

    ExternalTaskGrpc.ExternalTaskBlockingStub stub = ExternalTaskGrpc.newBlockingStub(channel);

    FetchAndLockReply fetchAndLockReply = stub.fetchAndLock(FetchAndLockRequest.newBuilder().setTopicName("foo").build());

    log.info(fetchAndLockReply.toString());

    channel.shutdown();
  }


}
