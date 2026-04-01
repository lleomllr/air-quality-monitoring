package com.example.demo.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.example.demo.repository.aqi",
        entityManagerFactoryRef = "timescaleEntityManagerFactory",
        transactionManagerRef = "timescaleTransactionManager"
)
public class TimescaleDataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.timescale")
    public DataSource timescaleDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean timescaleEntityManagerFactory(
            EntityManagerFactoryBuilder builder) {

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

        // Récupère la configuration du mode ddl depuis une variable d'environnement pour faciliter
        // l'exécution locale / CI sans casser la validation stricte dans d'autres environnements.
        String ddlAuto = System.getenv("SPRING_JPA_HBM2DDL_AUTO");
        if (ddlAuto == null) {
            ddlAuto = System.getenv("SPRING_JPA_HIBERNATE_DDL_AUTO");
        }
        if (ddlAuto == null || ddlAuto.isBlank()) {
            // comportement par défaut existant
            ddlAuto = "validate";
        }
        properties.put("hibernate.hbm2ddl.auto", ddlAuto);

        properties.put("hibernate.show_sql", "true");
        properties.put("hibernate.format_sql", "true");
        properties.put("hibernate.default_schema", "aqi");
        properties.put("hibernate.jdbc.time_zone", "UTC");
        properties.put("hibernate.temp.use_jdbc_metadata_defaults", "false");

        // Activer Hibernate Spatial (important pour PostGIS)
        properties.put("hibernate.spatial.dialect", "org.hibernate.spatial.dialect.postgis.PostgisDialect");

        return builder
                .dataSource(timescaleDataSource())
                .packages("com.example.demo.model.aqi")
                .persistenceUnit("timescale")
                .properties(properties)
                .build();
    }

    @Bean
    public PlatformTransactionManager timescaleTransactionManager(
            @Qualifier("timescaleEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf) {
        return new JpaTransactionManager(emf.getObject());
    }
}