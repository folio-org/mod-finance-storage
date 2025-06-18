package org.folio.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SecureStoreType {
  EPHEMERAL("EPHEMERAL"),
  AWS_SSM("AWS_SSM"),
  VAULT("VAULT"),
  FSSP("FSSP");

  private final String value;
}
