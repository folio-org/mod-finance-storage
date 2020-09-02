package org.folio.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({DAOConfiguration.class, ServicesConfiguration.class})
public class ApplicationConfig {

}
