package org.folio.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SecureStoreType {
  EPHEMERAL("EPHEMERAL"),
  VAULT("VAULT"),
  AWS_SSM("AWS_SSM");

  private final String value;
}
