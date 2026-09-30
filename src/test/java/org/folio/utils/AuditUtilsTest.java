package org.folio.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.folio.CopilotGenerated;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Metadata;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.Json;

@CopilotGenerated(model = "Claude Opus 5.5")
public class AuditUtilsTest {

  @Test
  void shouldReturnOnlyChangedEntitiesIgnoringMetadataAndVersion() {
    var metadataOnlyOriginal = budget();
    var metadataOnlyChanged = copy(metadataOnlyOriginal)
      .withMetadata(new Metadata().withUpdatedDate(new Date()).withUpdatedByUserId(UUID.randomUUID().toString()));
    var versionOnlyOriginal = budget();
    var versionOnlyChanged = copy(versionOnlyOriginal).withVersion(versionOnlyOriginal.getVersion() + 1);
    var changedOriginal = budget();
    var changed = copy(changedOriginal)
      .withBudgetStatus(Budget.BudgetStatus.FROZEN)
      .withVersion(changedOriginal.getVersion() + 1);
    var withoutOriginal = budget();

    var result = AuditUtils.getChangedEntities(List.of(metadataOnlyChanged, versionOnlyChanged, changed, withoutOriginal),
      List.of(changedOriginal, versionOnlyOriginal, metadataOnlyOriginal), Budget::getId);

    assertEquals(List.of(changed, withoutOriginal), result);
  }

  private Budget budget() {
    return new Budget()
      .withId(UUID.randomUUID().toString())
      .withVersion(1)
      .withName("History FY2026")
      .withBudgetStatus(Budget.BudgetStatus.ACTIVE)
      .withAllocated(100.0)
      .withMetadata(new Metadata().withCreatedDate(new Date()));
  }

  private Budget copy(Budget budget) {
    return Json.decodeValue(Json.encode(budget), Budget.class);
  }

}
