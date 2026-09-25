package com.technnext.hrms.config;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

public class DevTablePhysicalNamingStrategy implements PhysicalNamingStrategy {

    private static final String DEV_SUFFIX = "_Dev";

    private final boolean devSuffixEnabled;

    public DevTablePhysicalNamingStrategy() {
        this.devSuffixEnabled =
                Boolean.parseBoolean(
                        System.getenv().getOrDefault(
                                "HRMS_DEV_TABLE_SUFFIX",
                                "false"
                        )
                );
    }

    @Override
    public Identifier toPhysicalCatalogName(
            Identifier logicalName,
            JdbcEnvironment jdbcEnvironment) {
        return logicalName;
    }

    @Override
    public Identifier toPhysicalSchemaName(
            Identifier logicalName,
            JdbcEnvironment jdbcEnvironment) {
        return logicalName;
    }

    @Override
    public Identifier toPhysicalTableName(
            Identifier logicalName,
            JdbcEnvironment jdbcEnvironment) {

        if (!devSuffixEnabled || logicalName == null) {
            return logicalName;
        }

        String tableName = logicalName.getText();

        if (tableName.endsWith(DEV_SUFFIX)) {
            return Identifier.toIdentifier(tableName, true);
        }

        return Identifier.toIdentifier(
                tableName + DEV_SUFFIX,
                true
        );
    }

    @Override
    public Identifier toPhysicalSequenceName(
            Identifier logicalName,
            JdbcEnvironment jdbcEnvironment) {

        if (!devSuffixEnabled || logicalName == null) {
            return logicalName;
        }

        String sequenceName = logicalName.getText();

        if (sequenceName.endsWith("_dev")) {
            return Identifier.toIdentifier(sequenceName, false);
        }

        return Identifier.toIdentifier(
                sequenceName + "_dev",
                false
        );
    }

    @Override
    public Identifier toPhysicalColumnName(
            Identifier logicalName,
            JdbcEnvironment jdbcEnvironment) {
        return logicalName;
    }
}