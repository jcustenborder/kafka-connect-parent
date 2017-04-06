## Introduction

This project provides a parent pom for Kafka Connect projects.


## Support for integration testing

Place your integration testing code in `src/integration-test/java` and your integration testing resources in `src/integration-test/resources`
The *integration-test* will include these source items and enable the [Maven Failsafe Plugin](http://maven.apache.org/components/surefire/maven-failsafe-plugin/).

```bash
mvn clean compile test-compile integration-test failsafe:verify -P integration-test
```

