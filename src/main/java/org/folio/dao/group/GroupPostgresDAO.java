package org.folio.dao.group;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Group;
import org.folio.rest.jaxrs.model.GroupFundFiscalYear;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.interfaces.Results;

import java.util.List;
import java.util.UUID;

public class GroupPostgresDAO implements GroupDAO {
  private static final Logger logger = LogManager.getLogger();
  private static final String GROUPS_TABLE = "groups";
  private static final String GROUP_FUND_FY_TABLE = "group_fund_fiscal_year";

  @Override
  public Future<Group> createGroup(Group group, DBConn conn) {
    if (group.getId() == null) {
      group.setId(UUID.randomUUID().toString());
    }
    String id = group.getId();
    logger.debug("Trying to create group with id {}", id);
    return conn.saveAndReturnUpdatedEntity(GROUPS_TABLE, group.getId(), group)
      .onSuccess(fund -> logger.info("Successfully created a group with id {}", id))
      .onFailure(e -> logger.error("Creating a group with id {} failed", id, e));
  }

  @Override
  public Future<Void> updateGroup(Group group, String id, DBConn conn) {
    logger.debug("Trying to update group with id {}", id);
    return conn.update(GROUPS_TABLE, group, id)
      .onSuccess(fund -> logger.info("Successfully updated a group with id {}", id))
      .onFailure(e -> logger.error("Updating a group with id {} failed", id, e))
      .mapEmpty();
  }

  @Override
  public Future<List<GroupFundFiscalYear>> getGroupFundFiscalYears(String fundId, String currentFYId, DBConn conn) {
    logger.debug("Trying to get group fund fiscal years by fund id {} and fiscal year id {}", fundId, currentFYId);
    CriterionBuilder criterionBuilder = new CriterionBuilder("AND");
    criterionBuilder.with("fundId", fundId);
    criterionBuilder.with("fiscalYearId", currentFYId);
    Criterion criterion = criterionBuilder.build();
    return conn.get(GROUP_FUND_FY_TABLE, GroupFundFiscalYear.class, criterion)
      .map(Results::getResults)
      .onSuccess(gffys -> logger.info("Successfully retrieved {} group fund fiscal years", gffys.size()))
      .onFailure(e -> logger.error("Getting group fund fiscal years failed, criterion: {}", criterion, e));
  }

  @Override
  public Future<Void> updateBatchGroupFundFiscalYear(List<GroupFundFiscalYear> groupFundFiscalYears, DBConn conn) {
    List<String> ids = groupFundFiscalYears.stream().map(GroupFundFiscalYear::getId).toList();
    logger.debug("Trying update batch group fund fiscal years, ids={}", ids);
    return conn.updateBatch(GROUP_FUND_FY_TABLE, groupFundFiscalYears)
      .onSuccess(rowSet -> logger.info("Successfully updated {} batch group fund fiscal years", groupFundFiscalYears.size()))
      .onFailure(e -> logger.error("Update batch group fund fiscal years failed, ids={}", ids, e))
      .mapEmpty();
  }

}
