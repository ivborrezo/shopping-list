package dev.ivborrezo.shoppinglist.list.service.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

class LoggingDomainEventPublisherTest {

  private Logger publisherLogger;

  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    TransactionSynchronizationManager.clear();
    publisherLogger = (Logger) LoggerFactory.getLogger(LoggingDomainEventPublisher.class);
    appender = new ListAppender<>();
    appender.start();
    publisherLogger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    publisherLogger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void publish_withoutActiveTransaction_logsEventImmediately() {
    ObjectMapper objectMapper = new ObjectMapper();
    DomainEventPublisher publisher = new LoggingDomainEventPublisher(objectMapper);
    DomainEvent<Map<String, Object>> event = listCreatedEvent();

    publisher.publish(event);

    assertThat(appender.list).hasSize(1);
    ILoggingEvent logEvent = appender.list.get(0);
    assertThat(logEvent.getLevel()).isEqualTo(Level.INFO);
    assertThat(logEvent.getFormattedMessage())
        .contains("\"eventType\":\"list.created\"", "corr-123", "abc");
  }

  @Test
  void publish_withActiveTransaction_defersDeliveryToAfterCommit() {
    ObjectMapper objectMapper = new ObjectMapper();
    DomainEventPublisher publisher = new LoggingDomainEventPublisher(objectMapper);
    DomainEvent<Map<String, Object>> event = listCreatedEvent();

    TransactionSynchronizationManager.initSynchronization();
    try {
      TransactionSynchronizationManager.setActualTransactionActive(true);

      publisher.publish(event);

      assertThat(appender.list).isEmpty();

      TransactionSynchronizationManager.getSynchronizations()
          .forEach(TransactionSynchronization::afterCommit);

      assertThat(appender.list).hasSize(1);
      assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
  }

  @Test
  void publish_whenSerializationFails_logsErrorAndDoesNotPropagate() {
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    doThrow(new RuntimeException("serialization failure"))
        .when(objectMapper)
        .writeValueAsString(any());
    DomainEventPublisher publisher = new LoggingDomainEventPublisher(objectMapper);
    DomainEvent<Map<String, Object>> event = listCreatedEvent();

    assertThatNoException().isThrownBy(() -> publisher.publish(event));

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
  }

  @Test
  void eventType_serializesToPunctuatedValue() {
    ObjectMapper objectMapper = new ObjectMapper();

    assertThat(objectMapper.writeValueAsString(EventType.LIST_ITEM_ADDED))
        .isEqualTo("\"list.item.added\"");
    assertThat(objectMapper.writeValueAsString(EventType.LIST_DELETED))
        .isEqualTo("\"list.deleted\"");
  }

  private static DomainEvent<Map<String, Object>> listCreatedEvent() {
    return new DomainEvent<>(
        EventType.LIST_CREATED,
        "corr-123",
        Instant.parse("2026-01-01T00:00:00Z"),
        Map.of("listId", "abc"));
  }
}
