package org.folio.config;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.core.RestClient;
import org.springframework.context.annotation.Bean;

public class RestClientsConfiguration {

  @Bean
  public RestClient defaultRestClient() {
    return new RestClient(StringUtils.EMPTY);
  }

  @Bean
  public RestClient orderRolloverRestClient() {
    return new RestClient("/orders/rollover");
  }

  @Bean
  public RestClient userRestClient() {
    return new RestClient("/users");
  }

}
