package com.example.demo.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Test configuration: provides H2 in-memory datasources for tests when
 * property `app.test-mode=true` is set in test resources.
 */
@Configuration
public class TestDataSourceConfig {

    @Primary
    @Bean(name = "clientDataSource")
    public DataSource clientDataSource() {
        return DataSourceBuilder.create()
                .driverClassName("org.h2.Driver")
                .url("jdbc:h2:mem:clientdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
                .username("sa")
                .password("")
                .build();
    }

    @Bean(name = "timescaleDataSource")
    public DataSource timescaleDataSource() {
        return DataSourceBuilder.create()
                .driverClassName("org.h2.Driver")
                .url("jdbc:h2:mem:timescaledb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
                .username("sa")
                .password("")
                .build();
    }

    @Primary
    @Bean(name = "clientEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean clientEntityManagerFactory() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(true);
        vendorAdapter.setShowSql(false);

        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(clientDataSource());
        emf.setPackagesToScan("com.example.demo.model.client");
        emf.setPersistenceUnitName("client");
        emf.setJpaVendorAdapter(vendorAdapter);
        emf.setJpaPropertyMap(properties);
        return emf;
    }

    @Bean(name = "timescaleEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean timescaleEntityManagerFactory() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(true);
        vendorAdapter.setShowSql(false);

        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(timescaleDataSource());
        emf.setPackagesToScan("com.example.demo.model.airquality");
        emf.setPersistenceUnitName("timescale");
        emf.setJpaVendorAdapter(vendorAdapter);
        emf.setJpaPropertyMap(properties);
        return emf;
    }

    @Primary
    @Bean(name = "clientTransactionManager")
    public PlatformTransactionManager clientTransactionManager(LocalContainerEntityManagerFactoryBean clientEntityManagerFactory) {
        return new JpaTransactionManager(Objects.requireNonNull(clientEntityManagerFactory.getObject(), "client EntityManagerFactory is null"));
    }

    @Bean(name = "timescaleTransactionManager")
    public PlatformTransactionManager timescaleTransactionManager(LocalContainerEntityManagerFactoryBean timescaleEntityManagerFactory) {
        return new JpaTransactionManager(Objects.requireNonNull(timescaleEntityManagerFactory.getObject(), "timescale EntityManagerFactory is null"));
    }
}
