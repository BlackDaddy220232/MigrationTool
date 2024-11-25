package org.example.executor;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.db.ConnectionManager;
import org.example.db.MigrationManager;
import org.example.exception.DatabaseException;
import org.example.exception.MigrationException;
import org.example.file.ParseSql;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;


@Getter
@Slf4j
public class MigrationExecutor {

    private final ConnectionManager connectionManager;
    private final MigrationManager migrationManager;
    private final ParseSql parseSql;
    private final Connection connection;
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
    }
    public boolean executeMigrations() {
        List<File> migrationFiles = migrationManager.getPendingMigrations(connection);
        log.info("Starting migration execution...");
        try {
            connectionManager.acquireLock(connection);
            connection.setAutoCommit(false);
            for (File file : migrationFiles) {
                if (!executeSingleMigration(file, connection)) {
                    rollbackTransaction(connection);
                    return false;
                }
            }
            migrationManager.registerMigrations(migrationFiles, connection);
            commitTransaction(connection);
            connectionManager.releaseLock(connection);
            return true;

        } catch (SQLException exception) {
            throw new MigrationException("");
        }
    }
    public void rollbackMigrations(String version) {
        List<File> rollbackFileToExecute = migrationManager.getRollbackMigrations(version,connection);
        log.info("Starting rollback to version {}",version);
        try {
            connectionManager.acquireLock(connection);
            connection.setAutoCommit(false);
            for (File file : rollbackFileToExecute) {
                if (!executeSingleMigration(file, connection)) {
                    rollbackTransaction(connection);
                    return;
                }
            }
            migrationManager.deleteMigrations(rollbackFileToExecute.size(),connection);
            commitTransaction(connection);
            connectionManager.releaseLock(connection);

        } catch (SQLException exception) {
            log.error("Failed to rollback migrations" + exception.getMessage());
            throw new MigrationException("Failed to rollback migrations" + exception.getMessage());
        }
    }
    public void getStatus(){
        List<String> appliedMinagrations = migrationManager.getAppliedMigrations(connection);
        List<File> pendingMigration = migrationManager.getPendingMigrations(connection);
        log.info("Applied migration:");
        for (String filename:appliedMinagrations){
            log.info(filename);
        }
        log.info("Pending migration:");
        for (File file: pendingMigration){
            log.info(file.getName());
        }
    }
    public void init(){
        if(migrationManager.createTables(connection)){
            log.info("Tables have been created successfully");
        }
    }

    private boolean executeSingleMigration(File migrationFile, Connection connection) {
        String sqlCommand = parseSql.readSqlFileToString(migrationFile);
        log.info("Executing migration: {}", migrationFile.getName());
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCommand)) {
            preparedStatement.execute();
            return true;
        } catch (SQLException e) {
            log.error("Failed to execute migration {}: {}", migrationFile.getName(), e.getMessage());
            return false;
        }
    }

    private void commitTransaction(Connection connection) {
        try {
            connection.commit();
            log.info("Transaction committed successfully.");
        } catch (SQLException e) {
            log.error("Failed to commit transaction: {}", e.getMessage());
            throw new DatabaseException("Failed to commit transaction: " + e.getMessage());
        }
    }

    private void rollbackTransaction(Connection connection) {
        try {
            connection.rollback();
            log.warn("Transaction rolled back.");
        } catch (SQLException e) {
            log.error("Failed to rollback transaction: {}", e.getMessage());
            throw new DatabaseException("Rollback failed: " + e.getMessage());
        }
    }
}
