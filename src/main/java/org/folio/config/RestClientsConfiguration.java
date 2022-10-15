package org.folio.config;

import org.folio.rest.core.RestClient;
import org.springframework.context.annotation.Bean;

public class RestClientsConfiguration {

  @Bean
  public RestClient orderRolloverRestClient() {
    return new RestClient("/orders/rollover");
  }

  @Bean
  public RestClient configurationRestClient() {
    return new RestClient("/configurations/entries");
  }

  @Bean
  public RestClient userRestClient() {
    return new RestClient("/users");
  }

}
