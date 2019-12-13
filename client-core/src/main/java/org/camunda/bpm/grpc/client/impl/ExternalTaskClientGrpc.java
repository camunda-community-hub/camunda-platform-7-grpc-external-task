package org.camunda.bpm.grpc.client.impl;

import org.camunda.bpm.grpc.client.ExternalTaskClient;
import org.camunda.bpm.grpc.client.topic.TopicSubscriptionBuilder;
import org.camunda.bpm.grpc.client.topic.impl.TopicSubscriptionBuilderImpl;
import org.camunda.bpm.grpc.client.topic.impl.TopicSubscriptionManager;

public class ExternalTaskClientGrpc implements ExternalTaskClient {

  TopicSubscriptionManager topicSubscriptionManager;

  public ExternalTaskClientGrpc(TopicSubscriptionManager topicSubscriptionManager) {
    this.topicSubscriptionManager = topicSubscriptionManager;
  }

  public TopicSubscriptionBuilder subscribe(String topicName) {
    return new TopicSubscriptionBuilderImpl(topicName, topicSubscriptionManager);
  }

  public void stop() {
    topicSubscriptionManager.stop();
  }

  public void start() {
    topicSubscriptionManager.start();
  }

  public boolean isActive() {
    return topicSubscriptionManager.isRunning();
  }

  public TopicSubscriptionManager getTopicSubscriptionManager() {
    return topicSubscriptionManager;
  }

}
