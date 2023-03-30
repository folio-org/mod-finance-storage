package org.folio.rest.impl;

import java.util.Map;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ExpenseClass;
import org.folio.rest.jaxrs.model.ExpenseClassCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageExpenseClasses;
import org.folio.rest.persist.PgUtil;
import org.folio.service.expence.ExpenseClassService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class ExpenseClassAPI implements FinanceStorageExpenseClasses {
  public static final String EXPENSE_CLASS_TABLE = "expense_class";

  private ExpenseClassService expenseClassService;

  public ExpenseClassAPI(Vertx vertx, String tenantId) {
    expenseClassService = new ExpenseClassService(vertx, tenantId);
  }

  @Override
  @Validate
  public void getFinanceStorageExpenseClasses(String query, String totalRecords, @Min(0) @Max(2147483647) int offset, @Min(0) @Max(2147483647) int limit,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(EXPENSE_CLASS_TABLE, ExpenseClass.class, ExpenseClassCollection.class, query, offset, limit, okapiHeaders,
        vertxContext, FinanceStorageExpenseClasses.GetFinanceStorageExpenseClassesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageExpenseClasses(ExpenseClass entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    expenseClassService.createExpenseClass(entity, vertxContext, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageExpenseClassesById(
      @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$") String id,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(EXPENSE_CLASS_TABLE, ExpenseClass.class, id, okapiHeaders, vertxContext,
        FinanceStorageExpenseClasses.GetFinanceStorageExpenseClassesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageExpenseClassesById(
      @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$") String id,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(EXPENSE_CLASS_TABLE, id, okapiHeaders, vertxContext,
        FinanceStorageExpenseClasses.DeleteFinanceStorageExpenseClassesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageExpenseClassesById(
      @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$") String id,
      ExpenseClass entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    expenseClassService.updateExpenseClass(id, entity, vertxContext, asyncResultHandler);
  }
}
