package org.folio.dao.group;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Group;
import org.folio.rest.jaxrs.model.GroupFundFiscalYear;
import org.folio.rest.jaxrs.model.GroupFundFiscalYearBatchRequest;
import org.folio.rest.persist.DBConn;

import java.util.List;

public interface GroupDAO {
  Future<Group> createGroup(Group group, DBConn conn);
  Future<Void> updateGroup(Group group, String id, DBConn conn);
  Future<List<GroupFundFiscalYear>> getGroupFundFiscalYears(String fundId, String currentFYId, DBConn conn);
  Future<Void> updateBatchGroupFundFiscalYear(List<GroupFundFiscalYear> groupFundFiscalYears, DBConn conn);
  Future<List<GroupFundFiscalYear>> getGroupFundFiscalYearsByFundIds(GroupFundFiscalYearBatchRequest batchRequest, DBConn conn);
}
