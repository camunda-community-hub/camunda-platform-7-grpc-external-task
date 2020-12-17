# gRPC Server example
This server example provides a gRPC API for external tasks based on the [gRPC External Task Spring Boot Starter](../../starter).
It will start a Camunda BPM Runtime with Webapps using the [camunda-bpm-spring-boot-starter-webapp](https://docs.camunda.org/manual/latest/user-guide/spring-boot-integration/webapps/). The process engine will auto-deploy one process model that includes one defined external task.

The task can be consumed through the gRPC API by instances of the [gRPC External Task Client](../../client-core). 
You can use the provided [example client](../client) that works well with this example server.

Run it with

```Shell
mvn spring-boot:run
```
