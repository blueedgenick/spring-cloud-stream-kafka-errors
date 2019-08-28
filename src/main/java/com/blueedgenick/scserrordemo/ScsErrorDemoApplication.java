package com.blueedgenick.scserrordemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.support.ErrorMessage;

@SpringBootApplication
@EnableBinding(Source.class)
public class ScsErrorDemoApplication {

	public static void main(final String[] args) {
		SpringApplication.run(ScsErrorDemoApplication.class, args);
	}

  /**
   * Simplest possible message producer - writes a Kafka message every second
   */
  @Bean
  @InboundChannelAdapter(value = Source.OUTPUT,
      poller = @Poller(fixedDelay = "1000", maxMessagesPerPoll = "1"))
  public MessageSource<TimedTextMessage> timedMessageSource() {
    final int i = 0;
    return () ->
      MessageBuilder
        .withPayload(new TimedTextMessage(System.currentTimeMillis(), "Hello SCS World " + i))
        .build();
  }


  /**
   * Callback function invoked whenever a Producer error occurs.
   * 
   * @param em structured error object containing both the failing message and error details
   */
  @ServiceActivator(inputChannel = "errorChannel")
  public void handle(final ErrorMessage em) {
    System.out.println("** help me! **");
  }

  
  /**
   * Simple POJO to populate test messages
   */
  public static class TimedTextMessage {

    private final Long ts;
    private final String text;

    public TimedTextMessage(final Long ts, final String text) {
      super();
      this.ts = ts;
      this.text = text;
    }

    public Long getTime() {
      return ts;
    }

    public String getText() {
      return text;
    }

  }

}
