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
package org.camunda.bpm.grpc.client;

/**
 * <p>A fluent builder to configure the Camunda client</p>
 */
public interface ExternalTaskClientBuilder {

  /**
   * Base url of the Camunda BPM Platform gRPC API. This information is mandatory.
   *
   * @param baseUrl of the Camunda BPM Platform gRPC API
   * @return the builder
   */
  ExternalTaskClientBuilder baseUrl(String baseUrl);

  /**
   * A custom worker id the Workflow Engine is aware of. This information is optional.
   * Note: make sure to choose a unique worker id
   *
   * If not given or null, a worker id is generated automatically which consists of the
   * hostname as well as a random and unique 128 bit string (UUID).
   *
   * @param workerId the Workflow Engine is aware of
   * @return the builder
   */
  ExternalTaskClientBuilder workerId(String workerId);

  /**
   * Specifies the maximum amount of tasks that can be fetched within one request.
   * This information is optional. Default is 1.
   *
   * @param maxTasks which are supposed to be fetched within one request
   * @return the builder
   */
  ExternalTaskClientBuilder maxTasks(int maxTasks);

  /**
   * Specifies whether tasks should be fetched based on their priority or arbitrarily.
   * This information is optional. Default is <code>true</code>.
   *
   * @param usePriority when fetching and locking tasks
   * @return the builder
   */
  ExternalTaskClientBuilder usePriority(boolean usePriority);

  /**
   * Specifies the date format to de-/serialize date variables.
   *
   * @param dateFormat date format to be used
   * @return the builder
   */
  ExternalTaskClientBuilder dateFormat(String dateFormat);

  /**
   * @param lockDuration <ul>
   *                       <li> in milliseconds to lock the external tasks
   *                       <li> must be greater than zero
   *                       <li> the default lock duration is 20 seconds (20,000 milliseconds)
   *                       <li> is overridden by the lock duration configured on a topic subscription
   *                     </ul>
   * @return the builder
   */
  ExternalTaskClientBuilder lockDuration(long lockDuration);

  /**
   * Disables immediate fetching for external tasks after calling {@link #build} to bootstrap the client.
   * To start fetching {@link ExternalTaskClient#start()} must be called.
   *
   * @return the builder
   */
  ExternalTaskClientBuilder disableAutoFetching();

  /**
   * Bootstraps the Camunda client
   *
   * @throws ExternalTaskClientException
   * <ul>
   *   <li> if base url is null or string is empty
   *   <li> if hostname cannot be retrieved
   *   <li> if maximum amount of tasks is not greater than zero
   *   <li> if lock duration is not greater than zero
   * </ul>
   * @return the builder
   */
  ExternalTaskClient build();

}
