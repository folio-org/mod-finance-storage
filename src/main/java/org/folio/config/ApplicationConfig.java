package org.folio.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({DAOConfiguration.class, ServicesConfiguration.class, RestClientsConfiguration.class, SecureStoreConfiguration.class})
public class ApplicationConfig {
}
