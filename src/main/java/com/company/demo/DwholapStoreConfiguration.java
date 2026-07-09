package com.company.demo;

import io.jmix.core.JmixModules;
import io.jmix.core.Resources;
import io.jmix.data.impl.JmixEntityManagerFactoryBean;
import io.jmix.data.impl.JmixTransactionManager;
import io.jmix.data.persistence.DbmsSpecifics;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import jakarta.persistence.EntityManagerFactory;

import javax.sql.DataSource;

@Configuration
public class DwholapStoreConfiguration {

    @Bean
    @ConfigurationProperties("dwholap.datasource")
    DataSourceProperties dwholapDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "dwholap.datasource.hikari")
    DataSource dwholapDataSource(@Qualifier("dwholapDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    LocalContainerEntityManagerFactoryBean dwholapEntityManagerFactory(
            @Qualifier("dwholapDataSource") DataSource dataSource,
            JpaVendorAdapter jpaVendorAdapter,
            DbmsSpecifics dbmsSpecifics,
            JmixModules jmixModules,
            Resources resources
    ) {
        return new JmixEntityManagerFactoryBean("dwholap", dataSource, jpaVendorAdapter, dbmsSpecifics, jmixModules, resources);
    }

    @Bean
    JpaTransactionManager dwholapTransactionManager(@Qualifier("dwholapEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JmixTransactionManager("dwholap", entityManagerFactory);
    }
}
