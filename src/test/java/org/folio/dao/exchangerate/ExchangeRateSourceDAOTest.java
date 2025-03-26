package org.folio.dao.exchangerate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.folio.CopilotGenerated;
import org.folio.rest.jaxrs.model.ExchangeRateSource;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.helpers.LocalRowSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

@CopilotGenerated(partiallyGenerated = true)
public class ExchangeRateSourceDAOTest {

  private ExchangeRateSourceDAOImpl exchangeRateSourceDAO;
  private DBConn mockConn;

  @BeforeEach
  void setUp() {
    exchangeRateSourceDAO = new ExchangeRateSourceDAOImpl();
    mockConn = mock(DBConn.class);
  }

  @Test
  void getExchangeRateSource_returnsEmpty_whenNoData() {
    var rowSet = getEmptyRowSet();
    when(mockConn.execute(anyString())).thenReturn(Future.succeededFuture(rowSet));

    Future<Optional<ExchangeRateSource>> result = exchangeRateSourceDAO.getExchangeRateSource(mockConn);

    assertTrue(result.result().isEmpty());
  }

  @Test
  void getExchangeRateSource_returnsData_whenExists() {
    var rowSet = getRowSet(new JsonObject().put("id", UUID.randomUUID().toString()));
    when(mockConn.execute(anyString())).thenReturn(Future.succeededFuture(rowSet));

    Future<Optional<ExchangeRateSource>> result = exchangeRateSourceDAO.getExchangeRateSource(mockConn);

    assertTrue(result.result().isPresent());
  }

  @Test
  void saveExchangeRateSource_savesSuccessfully_whenNotExists() {
    var exchangeRateSource = new ExchangeRateSource();
    var rowSet = getEmptyRowSet();

    when(mockConn.execute(anyString())).thenReturn(Future.succeededFuture(rowSet));
    when(mockConn.saveAndReturnUpdatedEntity(anyString(), anyString(), any())).thenReturn(Future.succeededFuture(exchangeRateSource));

    Future<ExchangeRateSource> result = exchangeRateSourceDAO.saveExchangeRateSource(exchangeRateSource, mockConn);

    assertTrue(result.succeeded());
    assertEquals(exchangeRateSource, result.result());
  }

  @Test
  void saveExchangeRateSource_fails_whenAlreadyExists() {
    var exchangeRateSource = new ExchangeRateSource();
    var rowSet = getRowSet(new JsonObject().put("id", UUID.randomUUID().toString()));

    when(mockConn.execute(anyString())).thenReturn(Future.succeededFuture(rowSet));

    Future<ExchangeRateSource> result = exchangeRateSourceDAO.saveExchangeRateSource(exchangeRateSource, mockConn);

    assertTrue(result.failed());
  }

  @Test
  void updateExchangeRateSource_updatesSuccessfully_whenExists() {
    var exchangeRateSource = new ExchangeRateSource();
    var rowSet = getRowSet(true);

    when(mockConn.execute(anyString(), any())).thenReturn(Future.succeededFuture(rowSet));
    when(mockConn.update(anyString(), any(), anyString())).thenReturn(Future.succeededFuture());

    Future<Void> result = exchangeRateSourceDAO.updateExchangeRateSource(UUID.randomUUID().toString(), exchangeRateSource, mockConn);

    assertTrue(result.succeeded());
  }

  @Test
  void updateExchangeRateSource_fails_whenNotExists() {
    var exchangeRateSource = new ExchangeRateSource();
    var rowSet = getRowSet(false);

    when(mockConn.execute(anyString(), any())).thenReturn(Future.succeededFuture(rowSet));

    Future<Void> result = exchangeRateSourceDAO.updateExchangeRateSource(UUID.randomUUID().toString(), exchangeRateSource, mockConn);

    assertTrue(result.failed());
  }

  @Test
  void deleteExchangeRateSource_deletesSuccessfully_whenExists() {
    var rowSet = getRowSet(true);

    when(mockConn.execute(anyString(), any())).thenReturn(Future.succeededFuture(rowSet));
    when(mockConn.delete(anyString(), anyString())).thenReturn(Future.succeededFuture());

    Future<Void> result = exchangeRateSourceDAO.deleteExchangeRateSource(UUID.randomUUID().toString(), mockConn);

    assertTrue(result.succeeded());
  }

  @Test
  void deleteExchangeRateSource_fails_whenNotExists() {
    var rowSet = getRowSet(false);

    when(mockConn.execute(anyString(), any())).thenReturn(Future.succeededFuture(rowSet));

    Future<Void> result = exchangeRateSourceDAO.deleteExchangeRateSource(UUID.randomUUID().toString(), mockConn);

    assertTrue(result.failed());
  }

  private RowSet<Row> getEmptyRowSet() {
    return new LocalRowSet(0);
  }

  private RowSet<Row> getRowSet(JsonObject jsonObject) {
    var row = mock(Row.class);
    when(row.get(JsonObject.class, 0)).thenReturn(jsonObject);
    return new LocalRowSet(1)
      .withColumns(List.of("jsonb"))
      .withRows(List.of(row));
  }

  private RowSet<Row> getRowSet(Boolean boolValue) {
    var row = mock(Row.class);
    when(row.getBoolean(0)).thenReturn(boolValue);
    return new LocalRowSet(1)
      .withColumns(List.of("exists"))
      .withRows(List.of(row));
  }

}
