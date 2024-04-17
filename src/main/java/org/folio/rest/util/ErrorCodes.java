package org.folio.rest.util;

import org.folio.rest.jaxrs.model.Error;

public enum ErrorCodes {
  GENERIC_ERROR_CODE("genericError", "Generic error"),
  UNIQUE_FIELD_CONSTRAINT_ERROR("uniqueField{0}{1}Error", "Field {0} must be unique"),
  BUDGET_EXPENSE_CLASS_REFERENCE_ERROR("budgetExpenseClassReferenceError", "Can't delete budget that referenced with expense class"),
  MISSING_FUND_ID("missingFundId", "One of the fields toFundId or fromFundId must be specified"),
  ALLOCATION_MUST_BE_POSITIVE("allocationMustBePositive", "Allocation amount must be greater than zero"),
  CONFLICT("conflict", "Conflict when updating a record in table {0}: {1}"),
  BUDGET_NOT_FOUND_FOR_TRANSACTION("budgetNotFoundForTransaction", "Budget not found for pair fiscalYear-fundId"),
  BUDGET_RESTRICTED_EXPENDITURES_ERROR("budgetRestrictedExpendituresError", "Expenditure restriction does not allow this operation"),
  BUDGET_RESTRICTED_ENCUMBRANCE_ERROR("budgetRestrictedEncumbranceError", "Encumbrance restriction does not allow this operation"),
  PAYMENT_OR_CREDIT_HAS_NEGATIVE_AMOUNT("paymentOrCreditHasNegativeAmount", "A payment or credit has a negative amount");
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
