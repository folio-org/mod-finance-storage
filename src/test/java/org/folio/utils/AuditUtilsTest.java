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
  void shouldReturnOnlyChangedEntitiesIgnoringMetadata() {
    var unchangedOriginal = budget();
    var metadataOnlyChanged = copy(unchangedOriginal)
      .withMetadata(new Metadata().withUpdatedDate(new Date()).withUpdatedByUserId(UUID.randomUUID().toString()));
    var changedOriginal = budget();
    var changed = copy(changedOriginal).withBudgetStatus(Budget.BudgetStatus.FROZEN);
    var withoutOriginal = budget();

    var result = AuditUtils.getChangedEntities(List.of(metadataOnlyChanged, changed, withoutOriginal),
      List.of(changedOriginal, unchangedOriginal), Budget::getId);

    assertEquals(List.of(changed, withoutOriginal), result);
  }

  private Budget budget() {
    return new Budget()
      .withId(UUID.randomUUID().toString())
      .withName("History FY2026")
      .withBudgetStatus(Budget.BudgetStatus.ACTIVE)
      .withAllocated(100.0)
      .withMetadata(new Metadata().withCreatedDate(new Date()));
  }

  private Budget copy(Budget budget) {
    return Json.decodeValue(Json.encode(budget), Budget.class);
  }

}
