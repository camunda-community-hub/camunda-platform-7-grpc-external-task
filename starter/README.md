# Spring Boot Starter providing a gRPC API for external tasks
This starter provides a gRPC API for external tasks based on the Camunda BPM runtime.

Use it in your project by adding the following dependency to your Spring Boot application
```xml
<dependency>
  <groupId>org.camunda.bpm.extension.grpc.externaltask</groupId>
  <artifactId>camunda-bpm-grpc-external-task-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Configuration

The starter will configure a server to offer the API on port 6565 by default.

It is based on https://github.com/LogNet/grpc-spring-boot-starter. 

Please have a look at the configuration options over there to adjust your server accordingly.
