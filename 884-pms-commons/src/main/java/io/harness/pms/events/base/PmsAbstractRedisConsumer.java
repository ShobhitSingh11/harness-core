package io.harness.pms.events.base;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofSeconds;

import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class PmsAbstractRedisConsumer<T extends PmsAbstractMessageListener> implements PmsRedisConsumer {
  private static final int WAIT_TIME_IN_SECONDS = 10;
  private static final String CACHE_KEY = "%s_%s";
  private final Consumer redisConsumer;
  private final T messageListener;
  private AtomicBoolean shouldStop = new AtomicBoolean(false);
  private Cache<String, Integer> eventsCache;

  public PmsAbstractRedisConsumer(Consumer redisConsumer, T messageListener, Cache<String, Integer> eventsCache) {
    this.redisConsumer = redisConsumer;
    this.messageListener = messageListener;
    this.eventsCache = eventsCache;
  }

  @Override
  public void run() {
    log.info("Started the Consumer {}", this.getClass().getSimpleName());
    String threadName = this.getClass().getSimpleName() + "-handler-" + generateUuid();
    log.debug("Setting thread name to {}", threadName);
    Thread.currentThread().setName(threadName);

    try {
      do {
        while (getMaintenanceFlag()) {
          sleep(ofSeconds(1));
        }
        readEventsFrameworkMessages();
      } while (!Thread.currentThread().isInterrupted() && !shouldStop.get());
    } catch (Exception ex) {
      log.error("Consumer {} unexpectedly stopped", this.getClass().getSimpleName(), ex);
    }
  }

  private void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (EventsFrameworkDownException e) {
      log.error("Events framework is down for " + this.getClass().getSimpleName() + " consumer. Retrying again...", e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }

  private void pollAndProcessMessages() {
    List<Message> messages;
    String messageId;
    boolean messageProcessed;
    messages = redisConsumer.read(Duration.ofSeconds(WAIT_TIME_IN_SECONDS));
    for (Message message : messages) {
      messageId = message.getId();
      messageProcessed = handleMessage(message);
      if (messageProcessed) {
        redisConsumer.acknowledge(messageId);
      }
    }
  }

  private boolean handleMessage(Message message) {
    try {
      return processMessage(message);
    } catch (Exception ex) {
      // This is not evicted from events framework so that it can be processed
      // by other consumer if the error is a runtime error
      log.error(String.format("Error occurred in processing message with id %s", message.getId()), ex);
      return false;
    }
  }

  private boolean processMessage(Message message) {
    AtomicBoolean success = new AtomicBoolean(true);
    if (messageListener.isProcessable(message) && !isAlreadyProcessed(message)) {
      insertMessageInCache(message);
      if (!messageListener.handleMessage(message)) {
        success.set(false);
      }
    }
    return success.get();
  }

  private void insertMessageInCache(Message message) {
    try {
      eventsCache.put(String.format(CACHE_KEY, this.getClass().getSimpleName(), message.getId()), 1);
    } catch (Exception ex) {
      log.error("Exception occurred while storing message id in cache", ex);
    }
  }

  private boolean isAlreadyProcessed(Message message) {
    try {
      String key = String.format(CACHE_KEY, this.getClass().getSimpleName(), message.getId());
      boolean isProcessed = eventsCache.containsKey(key);
      if (isProcessed) {
        log.warn(String.format("Duplicate redis notification received to consumer [%s] with messageId [%s]",
            this.getClass().getSimpleName(), message.getId()));
        Integer count = eventsCache.get(key);
        if (count != null) {
          eventsCache.put(String.format(CACHE_KEY, this.getClass().getSimpleName(), message.getId()), count + 1);
        }
      }
      return isProcessed;
    } catch (Exception ex) {
      log.error("Exception occurred while checking for duplicate notification", ex);
      return false;
    }
  }

  public void shutDown() {
    shouldStop.set(true);
  }
}
