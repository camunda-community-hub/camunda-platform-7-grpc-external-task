package org.camunda.bpm.grpc;

import org.camunda.bpm.spring.boot.starter.annotation.EnableProcessApplication;
import org.camunda.bpm.spring.boot.starter.event.PostDeployEvent;
import org.camunda.bpm.spring.boot.starter.event.PreUndeployEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.EventListener;

@SpringBootApplication
@EnableProcessApplication("grpc-example")
public class CamundaApplication  {

  @Autowired
  private WaitingClientInformer informer;

  private ExternalTaskCreationListener externalTaskCreationListener;

  public static void main(String... args) {
    SpringApplication.run(CamundaApplication.class, args);
  }

  @EventListener
  public void onPostDeploy(PostDeployEvent event) {
    externalTaskCreationListener = new ExternalTaskCreationListener(informer);
    externalTaskCreationListener.start();
  }

  @EventListener
  public void onPreUndeploy(PreUndeployEvent event) {
    if (externalTaskCreationListener != null) {
      externalTaskCreationListener.shutdown();
      externalTaskCreationListener = null;
    }
  }
}
