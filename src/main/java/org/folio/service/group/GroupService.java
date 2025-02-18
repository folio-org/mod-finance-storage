package org.folio.service.group;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.group.GroupDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Group;
import org.folio.rest.jaxrs.model.GroupFundFiscalYear;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.rest.persist.DBConn;

import io.vertx.core.Future;

public class GroupService {

  private static final Logger logger = LogManager.getLogger(GroupService.class);

  private final DBClientFactory dbClientFactory;
  private final GroupDAO groupDAO;

  public GroupService(DBClientFactory dbClientFactory, GroupDAO groupDAO) {
    this.dbClientFactory = dbClientFactory;
    this.groupDAO = groupDAO;
  }

  public Future<Group> createGroup(Group entity, RequestContext requestContext) {
    logger.debug("createGroup:: Trying to create group");
    DBClient client = dbClientFactory.getDbClient(requestContext);
    return client.withTrans(conn -> groupDAO.createGroup(entity, conn));
  }

  public Future<Void> updateGroup(Group entity, String id, RequestContext requestContext) {
    logger.debug("updateGroup:: Trying to update group with id {}", id);
    DBClient client = dbClientFactory.getDbClient(requestContext);
    return client.withTrans(conn -> groupDAO.updateGroup(entity, id, conn));
  }

  public Future<Void> updateBudgetIdForGroupFundFiscalYears(Budget budget, DBConn conn) {
    logger.debug("updateBudgetIdForGroupFundFiscalYears:: budget id: {}", budget.getId());
    return groupDAO.getGroupFundFiscalYears(budget.getFundId(), budget.getFiscalYearId(), conn)
      .compose(gffys -> processGroupFundFyUpdate(budget, gffys, conn));
  }

  private Future<Void> processGroupFundFyUpdate(Budget budget, List<GroupFundFiscalYear> groupFundFiscalYears, DBConn conn) {
    if (groupFundFiscalYears.isEmpty()) {
      return Future.succeededFuture();
    }
    for (GroupFundFiscalYear gffy : groupFundFiscalYears) {
      gffy.setBudgetId(budget.getId());
    }
    return groupDAO.updateBatchGroupFundFiscalYear(groupFundFiscalYears, conn);
  }
}
