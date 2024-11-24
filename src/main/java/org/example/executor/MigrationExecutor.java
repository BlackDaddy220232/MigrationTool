package org.example.executor;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.db.ConnectionManager;
import org.example.db.MigrationManager;
import org.example.exception.MigrationException;
import org.example.file.MigrationFileReader;
import org.example.file.ParseSql;
import org.example.utils.PropertiesUtils;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;


@Getter
@Slf4j
public class MigrationExecutor {

    private final ConnectionManager connectionManager;
    private final MigrationManager migrationManager;
    private final ParseSql parseSql;
    private final Connection connection;
    private List<File> migrationFiles;
    private final Properties properties;
    private static final String INSERT_NEW_MIGRATION_QUERY = "INSERT INTO applied_migrations (migration_name, applied_at, created_by) VALUES (?, ?, ?)";

    public MigrationExecutor(ConnectionManager connectionManager,
                             MigrationManager migrationManager,
                             Properties properties) {
        this.connectionManager = connectionManager;
        this.migrationManager = migrationManager;
        this.properties = properties;

        this.parseSql = new ParseSql();
        this.connection = connectionManager.getConnection();
        this.migrationFiles = migrationManager.getPendingMigrations(connection);
    }
    public boolean executeMigrations() {
        log.info("Starting migration execution...");
        try {
            connection.setAutoCommit(false);
            for (File file : migrationFiles) {
                if (!executeSingleMigration(file, connection)) {
                    connection.rollback();
                    return false;
                }
            }
            migrationManager.registerMigrations(migrationFiles, connection);
            commitTransaction(connection);
            return true;

        } catch (SQLException exception) {
            throw new MigrationException("");
        }
    }
    public boolean rollbackMigrations(String version) {
        List<File> rollbackFileToExecute = migrationManager.getRollbackMigrations("1_0",connection);
        log.info("Starting rollback to version {}",version);
        try {
            connection.setAutoCommit(false);
            for (File file : rollbackFileToExecute) {
                if (!executeSingleMigration(file, connection)) {
                    connection.rollback();
                    return false;
                }
            }
            migrationManager.deleteMigrations(rollbackFileToExecute.size(),connection);
            commitTransaction(connection);
            return true;

        } catch (SQLException exception) {
            throw new MigrationException("");
        }
    }

    private boolean executeSingleMigration(File migrationFile, Connection connection) {
        String sqlCommand = parseSql.sqlConverter(migrationFile);
        log.info("Executing migration: {}", migrationFile.getName());
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCommand)) {
            preparedStatement.execute();
            return true;
        } catch (SQLException e) {
            log.error("Failed to execute migration {}: {}", migrationFile.getName(), e.getMessage(), e);
            return false;
        }
    }

    private void commitTransaction(Connection connection) {
        try {
            connection.commit();
            log.info("Transaction committed successfully.");
        } catch (SQLException e) {
            log.error("Failed to commit transaction: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to commit transaction: " + e.getMessage(), e);
        }
    }

    private void rollbackTransaction(Connection connection) {
        try {
            connection.rollback();
            log.warn("Transaction rolled back.");
        } catch (SQLException e) {
            log.error("Failed to rollback transaction: {}", e.getMessage(), e);
            throw new RuntimeException("Rollback failed: " + e.getMessage(), e);
        }
    }

    private void handleExecutionError(Connection connection, SQLException e) {
        log.error("Error during migration execution: {}", e.getMessage(), e);
        rollbackTransaction(connection);
    }
    private String extractVersionFromFileName(String fileName) {
        // Предполагаем, что версия идет сразу после префикса "V" или "UNDO"
        String versionPart = fileName.replaceAll("[^0-9_]", ""); // Убираем все символы, кроме цифр и подчеркиваний
        return versionPart;
    }
}
