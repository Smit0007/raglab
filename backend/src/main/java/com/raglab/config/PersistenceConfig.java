package com.raglab.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Persistence configuration enabling Spring Data JPA Repositories and Transaction Management.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.raglab.domain.repository")
@EnableTransactionManagement
public class PersistenceConfig {
}
