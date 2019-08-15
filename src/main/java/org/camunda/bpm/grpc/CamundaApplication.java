package org.camunda.bpm.grpc;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.util.SingleConsumerCondition;
import org.camunda.bpm.spring.boot.starter.annotation.EnableProcessApplication;
import org.camunda.bpm.spring.boot.starter.event.PostDeployEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.EventListener;

@SpringBootApplication
@EnableProcessApplication("camunda.bpm.grpc.external.task.server")
public class CamundaApplication  {

  @Autowired
  private ProcessEngine processEngine;

  @Autowired
  private WaitingClientInformer informer;

  public static void main(String... args) {
    SpringApplication.run(CamundaApplication.class, args);
  }

  @EventListener
  public void onPostDeploy(PostDeployEvent event) {
    ProcessEngineImpl.EXT_TASK_CONDITIONS.addConsumer(new SingleConsumerCondition(new Thread(new Runnable() {

      @Override
      public void run() {
        informer.informClients();
      }
    })));

    processEngine.getRuntimeService().startProcessInstanceByKey("camunda.bpm.grpc.external.task.server");
    processEngine.getRuntimeService().startProcessInstanceByKey("camunda.bpm.grpc.external.task.server");
    processEngine.getRuntimeService().startProcessInstanceByKey("camunda.bpm.grpc.external.task.server");
  }

}
