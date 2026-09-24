package org.folio.service.fund;

import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.fund.FundDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundAuditEvent;
import org.folio.rest.persist.DBConn;
import org.folio.service.audit.AuditOutboxService;

import java.util.List;
import java.util.UUID;

public class StorageFundService implements FundService {
  private static final Logger logger = LogManager.getLogger();

  private final FundDAO fundDAO;
  private final AuditOutboxService auditOutboxService;

  public StorageFundService(FundDAO fundDAO, AuditOutboxService auditOutboxService) {
    this.fundDAO = fundDAO;
    this.auditOutboxService = auditOutboxService;
  }

  @Override
  public Future<Fund> createFund(Fund fund, RequestContext requestContext) {
    if (StringUtils.isBlank(fund.getId())) {
      fund.setId(UUID.randomUUID().toString());
    }
    logger.debug("Trying to create fund '{}'", fund.getId());
    var okapiHeaders = requestContext.getHeaders();
    return requestContext.toDBClient()
      .withTrans(conn -> fundDAO.createFund(fund, conn)
        .compose(createdFund -> auditOutboxService
          .saveFundOutboxLog(conn, createdFund, FundAuditEvent.Action.CREATE)
          .map(createdFund)))
      .onSuccess(createdFund -> auditOutboxService.processOutboxEventLogs(okapiHeaders, requestContext.getContext()));
  }

  @Override
  public Future<Fund> getFundById(String fundId, DBConn conn) {
    return fundDAO.getFundById(fundId, conn);
  }

  @Override
  public Future<List<Fund>> getFundsByIds(List<String> ids, DBConn conn) {
    return fundDAO.getFundsByIds(ids, conn);
  }

  @Override
  public Future<Void> updateFund(Fund fund, RequestContext requestContext) {
    logger.debug("Trying to update fund '{}'", fund.getId());
    var okapiHeaders = requestContext.getHeaders();
    return requestContext.toDBClient()
      .withTrans(conn -> fundDAO.getFundById(fund.getId(), conn)
        .compose(originalFund -> updateFundWithRelatedBudgets(fund, originalFund, conn)
          .compose(v -> auditOutboxService
            .saveFundOutboxLog(conn, fund, originalFund, FundAuditEvent.Action.EDIT))))
      .onSuccess(v -> auditOutboxService.processOutboxEventLogs(okapiHeaders, requestContext.getContext()));
  }

  @Override
  public Future<Void> updateFunds(List<Fund> funds, DBConn conn) {
    logger.debug("updateFunds:: Trying to update '{}' fund(s) with minimal changes", funds.size());
    return fundDAO.updateFunds(funds, conn);
  }

  private Future<Void> updateFundWithRelatedBudgets(Fund fund, Fund originalFund, DBConn conn) {
    if (originalFund.getFundStatus() == fund.getFundStatus()) {
      return fundDAO.updateFund(fund, conn);
    }
    logger.info("updateFund:: Fund '{}' status has been changed to '{}'", fund.getId(), fund.getFundStatus());
    return fundDAO.updateRelatedCurrentFYBudgets(fund, conn)
      .compose(v -> fundDAO.updateFund(fund, conn));
  }
}
