## Introduction

An experiment with using the Spring Cloud Stream abstractions for producing messages to Apache Kafka. 

Originally created to investigate:

1. the default Producer settings used by the Spring libraries

1. how error-handling can best be configured.

## Running

It's a very basic Spring Boot app so dead simple to run - there's only one class and it has a `main` method :smile:. I mostly chose to run it from within my IDE while testing.

You'll need a Kafka Broker running somewhere for it to Produce messages to. If you're not running that broker on localhost on the default port (9092) then you'll need to adjust the
`spring.cloud.stream.kafka.binder.brokers` entry in the `application.yml` file to reflect the location of your broker.

## Producer settings

Notice that the entire set of Producer properties is, by default, logged when the app starts up. In particular, observe that the default number of retries is 0 and the default number of acks is set to 1.

### Why this matters

This is generally considered a poor choice for clients writing to any kind of distributed data store, 
and that is equally true in the Kafka world. 
These kind of distributed systems rarely have a strictly-binary availability mode i.e. 'up' or 'down'. 
Instead, by operating across a group or cluster of machines they seek to provide "at least some" availability, even when a subset of the cluster members are offline - perhaps as part of a rolling restart, for example.
In these situations it is desirable to have clients retry an operation against a server which is currently unavailable as it's responsibilities will generally be assumed by another member of the cluster in short order.

When working with Kafka it is therefore desirable to adjust the number of retries to a non-zero number. 
Note that recent versions of Kafka actually default this to MAX_INT (2147483647).

(For the curious, the time between retries is governed by a `retry.backoff.ms` value, set separately, default 100ms). 
Note that only so-called transient errors are retried.

Additionally, to ensure successful and durable recording of the messages your app is producing.
it is desirable to set the number of acks (acknowledgements) to `all`. 
This will force your client to wait for acknowledgements from all of the brokers responsible for recording a copy of it's messages before deeming them to have been durably saved. This can be important in several subtle but not uncommon edge cases, such as one broker or it's network becoming overloaded and browning out its connections with the other brokers or with zookeeper. This can cause the rest of the cluster to 'jettison' this particular broker, with the result that messages it has recorded to disk but not yet replicated to other brokers may be lost when it re-joins the cluster.

### Recommendations for reliable message producing

Add entries to your `application.yml`, under `spring.cloud.stream.kafka.binder`, for `required-acks: all` and `producer-properties.retries: 2147483647`.

>     kafka:
>       binder:
>          brokers: localhost
>          required-acks: all
>          producer-properties:
>            retries: 2
            
Familiarize yourself with the various producer properties related to durable message sending documented at
 (https://docs.confluent.io/current/installation/configuration/producer-configs.html). **NOTE** in particular the interaction of
  a non-zero value for the number of retries and the setting for `max.in.flight.requests.per.connection`, which you may want to adjust down to 1
  in the event that you wish to preserve message ordering even in the face of transient non-delivery-and-retry situations.


## Producer Error Handling

If an error occurs while producing a message to Kafka the default SCS behavior appears to be to write a log message and then continue processing anyway as if nothing had happened. 
You can easily simulate this by starting this test app, letting it run for a few seconds to send some messages, then stopping your local broker. After a brief timeout you should see something like this in the output

>
```
2019-08-27 21:22:42.526 ERROR 8588 --- [ad | producer-2] o.s.k.support.LoggingProducerListener    : Exception thrown when sending a message with key='null' and payload='{123, 34, 116, 101, 120, 116, 34, 58, 34, 72, 101, 108, 108, 111, 32, 83, 67, 83, 32, 87, 111, 114, ...' to topic scstest:

org.apache.kafka.common.errors.TimeoutException: Expiring 30 record(s) for scstest-0: 30014 ms has passed since batch creation plus linger time
```

Sometimes however you want your application to be informed of this error and allow it a chance to take some specific action of your choosing.
The good news is that, although somewhat non-obvious, this **is* possible in SCS!
You will need to make **two** changes in your app to have it be notified whenever a Producer error occurs:

1. Add a handler method with the `@ServiceActivator(inputChannel = "errorChannel")` annotation. This will be invoked whenever an error occurs producing to any topic from your app. If you want to have separate handlers for writing to different topics, and you know some Spring Integration, this is also possible to set up but beyond what I have space to cover here.

```java
  /**
   * Callback function invoked whenever a Producer error occurs.
   * 
   * @param em structured error object containing both the failing message and error details
   */
  @ServiceActivator(inputChannel = "errorChannel")
  public void handle(final ErrorMessage em) {
    System.out.println("** help me! **");
  }
```
2. Crucially the annotated method alone is not enough - it won't be invoked unless you also add another entry to the `application.yml`:

>     spring:
>       cloud:
>         stream:
>           bindings:
>             output:
>               producer:
>                 error-channel-enabled: true

That's it! Now your app will loudly cry for help on its STDOUT whenever Producer errors are encountered. (you _were_ going to implement something smarter than my simple example here, weren't you ? :grin:)

