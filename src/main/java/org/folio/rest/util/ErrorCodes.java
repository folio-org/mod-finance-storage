package org.folio.rest.util;

import org.folio.rest.jaxrs.model.Error;

public enum ErrorCodes {
  GENERIC_ERROR_CODE("genericError", "Generic error"),
  UNIQUE_FIELD_CONSTRAINT_ERROR("uniqueField{0}{1}Error", "Field {0} must be unique"),
  NOT_ENOUGH_MONEY_FOR_TRANSFER("notEnoughMoneyForTransferError", "Transfer was not successful. There is not enough money Available in the budget to complete this Transfer."),
  NOT_ENOUGH_MONEY_FOR_ALLOCATION("notEnoughMoneyForAllocationError", "Allocation was not successful. There is not enough money Available in the budget to complete this Allocation."),
  BUDGET_EXPENSE_CLASS_REFERENCE_ERROR("budgetExpenseClassReferenceError", "Can't delete budget that referenced with expense class"),
  MISSING_FUND_ID("missingFundId", "One of the fields toFundId or fromFundId must be specified"),
  MUST_BE_POSITIVE("mustBePositive", "Value must be greater than zero"),
  CONFLICT("conflict", "Conflict when updating a record in table {0}: {1}"),
  BUDGET_NOT_FOUND_FOR_TRANSACTION("budgetNotFoundForTransaction", "Budget not found for pair fiscalYear-fundId"),
  OUTDATED_FUND_ID_IN_ENCUMBRANCE("outdatedFundIdInEncumbrance",
    "Could not find the budget for the encumbrance. The encumbrance fund id is probably not matching the fund id in the invoice line.");
  private final String code;
  private final String description;

  ErrorCodes(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public String getCode() {
    return code;
  }

  @Override
  public String toString() {
    return code + ": " + description;
  }

  public Error toError() {
    return new Error().withCode(code).withMessage(description);
  }
}
