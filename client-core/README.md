# External task client consuming a gRPC API
This client core consumes a gRPC API for external tasks provided by the [gRPC Spring Boot Starter](../starter).

Use it in your project by adding the following dependency to your application
```xml
<dependency>
  <groupId>org.camunda.bpm.grpc.externaltask</groupId>
  <artifactId>camunda-bpm-grpc-external-task-client-core</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Configuration
The client is based on the supported [Camunda External Task Client for Java](https://github.com/camunda/camunda-external-task-client-java/) and can be configured in the same way. In fact, you can (except for some technically inherent differences) simply exchange the defined client in your application by replacing
```java
ExternalTaskClient.create()
  .baseUrl("<your-url>")
  .workerId("<your-worker-id>")
  .lockDuration(<duration>)
  .disableAutoFetching()
  .build();
```

with 

```java
ExternalTaskClientGrpc.create()
  .baseUrl("<your-url>")
  .workerId("<your-worker-id>")
  .lockDuration(<duration>)
  .disableAutoFetching()
  .build();
```

Currently unsupported for the gRPC client compared to the Java client are the following:

* defining interceptors (e.g. for authentication) - this is work in progress
* `asyncResponseTimeout` and backoff strategy - this is obsolete as the gRPC communication works with bi-directional streams and long polling is thus not necessary
