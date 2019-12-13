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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import org.camunda.bpm.grpc.client.ExternalTaskClientBuilder;
import org.camunda.bpm.grpc.client.topic.impl.TopicSubscriptionManager;

public class ExternalTaskClientBuilderImpl implements ExternalTaskClientBuilder {

  protected static final ExternalTaskClientLogger LOG = ExternalTaskClientLogger.CLIENT_LOGGER;

  protected String baseUrl;
  protected String workerId;
  protected int maxTasks;
  protected boolean usePriority;
  protected long lockDuration;
  protected boolean isAutoFetchingEnabled;

  protected String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

  protected TopicSubscriptionManager topicSubscriptionManager;

  public ExternalTaskClientBuilderImpl() {
    // default values
    this.maxTasks = 1;
    this.usePriority = true;
    this.lockDuration = 20_000;
    this.isAutoFetchingEnabled = true;
  }

  @Override
  public ExternalTaskClientBuilder baseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
    return this;
  }

  @Override
  public ExternalTaskClientBuilder workerId(String workerId) {
    this.workerId = workerId;
    return this;
  }

  @Override
  public ExternalTaskClientBuilder maxTasks(int maxTasks) {
    this.maxTasks = maxTasks;
    return this;
  }

  @Override
  public ExternalTaskClientBuilder usePriority(boolean usePriority) {
    this.usePriority = usePriority;
    return this;
  }

  @Override
  public ExternalTaskClientBuilder dateFormat(String dateFormat) {
    this.dateFormat = dateFormat;
    return this;
  }

  @Override
  public ExternalTaskClientBuilder lockDuration(long lockDuration) {
    this.lockDuration = lockDuration;
    return this;
  }

  @Override
  public ExternalTaskClientBuilder disableAutoFetching() {
    this.isAutoFetchingEnabled = false;
    return this;
  }

  @Override
  public ExternalTaskClientGrpc build() {
    if (maxTasks <= 0) {
      throw LOG.maxTasksNotGreaterThanZeroException(maxTasks);
    }

    if (lockDuration <= 0L) {
      throw LOG.lockDurationIsNotGreaterThanZeroException(lockDuration);
    }

    if (baseUrl == null || baseUrl.isEmpty()) {
      throw LOG.baseUrlNullException();
    }

    initBaseUrl();
    initWorkerId();
    // initObjectMapper();
    // initEngineClient();
    // initVariableMappers();
    initTopicSubscriptionManager();

    return new ExternalTaskClientGrpc(topicSubscriptionManager);
  }

  protected void initBaseUrl() {
    baseUrl = sanitizeUrl(baseUrl);
  }

  protected String sanitizeUrl(String url) {
    url = url.trim();
    if (url.endsWith("/")) {
      url = url.replaceAll("/$", "");
      url = sanitizeUrl(url);
    }
    return url;
  }

  protected void initWorkerId() {
    if (workerId == null) {
      String hostname = checkHostname();
      this.workerId = hostname + UUID.randomUUID();
    }
  }

  protected void initTopicSubscriptionManager() {
    topicSubscriptionManager = new TopicSubscriptionManager(workerId, maxTasks, baseUrl, usePriority, lockDuration);

    if (isAutoFetchingEnabled()) {
      topicSubscriptionManager.start();
    }
  }

  protected String checkHostname() {
    String hostname;
    try {
      hostname = getHostname();
    } catch (UnknownHostException e) {
      throw LOG.cannotGetHostnameException(e);
    }

    return hostname;
  }

  protected String getHostname() throws UnknownHostException {
    return InetAddress.getLocalHost().getHostName();
  }

  protected String getBaseUrl() {
    return baseUrl;
  }

  protected String getWorkerId() {
    return workerId;
  }

  protected int getMaxTasks() {
    return maxTasks;
  }

  protected boolean isUsePriority() {
    return usePriority;
  }

  protected long getLockDuration() {
    return lockDuration;
  }

  protected String getDateFormat() {
    return dateFormat;
  }

  protected boolean isAutoFetchingEnabled() {
    return isAutoFetchingEnabled;
  }

  protected TopicSubscriptionManager getTopicSubscriptionManager() {
    return topicSubscriptionManager;
  }

}
