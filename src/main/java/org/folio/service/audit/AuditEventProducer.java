package org.folio.service.audit;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.kafka.SimpleKafkaProducerManager;
import org.folio.kafka.services.KafkaProducerRecordBuilder;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetAuditEvent;
import org.folio.rest.jaxrs.model.EventTopic;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundAuditEvent;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;

public class AuditEventProducer {

  private static final Logger logger = LogManager.getLogger();

  private final KafkaConfig kafkaConfig;

  public AuditEventProducer(KafkaConfig kafkaConfig) {
    this.kafkaConfig = kafkaConfig;
  }

  public Future<Void> sendFundEvent(Fund fund, Fund originalFund, FundAuditEvent.Action eventAction, Map<String, String> okapiHeaders) {
    var event = getAuditEvent(fund, originalFund, eventAction);
    logger.info("sendFundEvent:: Sending event with id '{}' and fundId '{}' to Kafka", event.getId(), fund.getId());
    return sendToKafka(EventTopic.ACQ_FUND_CHANGED, event.getFundId(), event, okapiHeaders)
      .onFailure(t -> logger.warn("sendFundEvent:: Failed to send event with id '{}' and fundId '{}' to Kafka",
        event.getId(), fund.getId(), t));
  }

  public Future<Void> sendBudgetEvent(Budget budget, Budget originalBudget, BudgetAuditEvent.Action eventAction,
                                      Map<String, String> okapiHeaders) {
    var event = getAuditEvent(budget, originalBudget, eventAction);
    logger.info("sendBudgetEvent:: Sending event with id '{}' and budgetId '{}' to Kafka", event.getId(), budget.getId());
    return sendToKafka(EventTopic.ACQ_BUDGET_CHANGED, event.getBudgetId(), event, okapiHeaders)
      .onFailure(t -> logger.warn("sendBudgetEvent:: Failed to send event with id '{}' and budgetId '{}' to Kafka",
        event.getId(), budget.getId(), t));
  }

  FundAuditEvent getAuditEvent(Fund fund, Fund originalFund, FundAuditEvent.Action eventAction) {
    var metadata = fund.getMetadata();
    var event = new FundAuditEvent()
      .withId(UUID.randomUUID().toString())
      .withAction(eventAction)
      .withFundId(fund.getId())
      .withEventDate(new Date())
      .withActionDate(metadata != null ? metadata.getUpdatedDate() : null)
      .withUserId(metadata != null ? metadata.getUpdatedByUserId() : null)
      .withFundSnapshot(fund);
    if (originalFund != null) {
      restoreCreationMetadata(metadata, originalFund.getMetadata());
      event.setOriginalFundSnapshot(originalFund);
    }
    return event;
  }

  BudgetAuditEvent getAuditEvent(Budget budget, Budget originalBudget, BudgetAuditEvent.Action eventAction) {
    var metadata = budget.getMetadata();
    var event = new BudgetAuditEvent()
      .withId(UUID.randomUUID().toString())
      .withAction(eventAction)
      .withBudgetId(budget.getId())
      .withEventDate(new Date())
      .withActionDate(metadata != null ? metadata.getUpdatedDate() : null)
      .withUserId(metadata != null ? metadata.getUpdatedByUserId() : null)
      .withBudgetSnapshot(budget);
    if (originalBudget != null) {
      restoreCreationMetadata(metadata, originalBudget.getMetadata());
      event.setOriginalBudgetSnapshot(originalBudget);
    }
    return event;
  }

  /**
   * Restores the creation fields on an edited entity's snapshot. The PUT body reaches us with metadata RMB
   * stamped from the request headers, so its createdDate/createdByUserId describe the edit rather than the
   * original create.
   */
  private void restoreCreationMetadata(Metadata snapshot, Metadata original) {
    if (snapshot == null || original == null) {
      return;
    }
    snapshot.withCreatedDate(original.getCreatedDate())
      .withCreatedByUserId(original.getCreatedByUserId());
  }

  private Future<Void> sendToKafka(EventTopic eventTopic, String key, Object eventPayload, Map<String, String> okapiHeaders) {
    var tenantId = TenantTool.tenantId(okapiHeaders);
    var topicName = buildTopicName(kafkaConfig.getEnvId(), tenantId, eventTopic.value());
    KafkaProducerRecord<String, String> kafkaProducerRecord = new KafkaProducerRecordBuilder<String, Object>(tenantId)
      .key(key)
      .value(eventPayload)
      .topic(topicName)
      .propagateOkapiHeaders(okapiHeaders)
      .build();

    var producerManager = new SimpleKafkaProducerManager(Vertx.currentContext().owner(), kafkaConfig);
    KafkaProducer<String, String> producer = producerManager.createShared(topicName);
    return producer.send(kafkaProducerRecord)
      .onSuccess(s -> logger.info("sendToKafka:: Event for {} with id '{}' has been sent to kafka topic '{}'", eventTopic, key, topicName))
      .onFailure(t -> logger.error("sendToKafka:: Failed to send event for {} with id '{}' to kafka topic '{}'", eventTopic, key, topicName, t))
      .onComplete(reply -> producer.end().onComplete(v -> producer.close()))
      .mapEmpty();
  }

  private String buildTopicName(String envId, String tenantId, String eventType) {
    return KafkaTopicNameHelper.formatTopicName(envId, KafkaTopicNameHelper.getDefaultNameSpace(), tenantId, eventType);
  }

}
