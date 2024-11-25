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

/**
 * The {@code MigrationExecutor} class is responsible for executing, rolling back,
 * and tracking database migrations. It handles the processing of SQL migration scripts
 * and provides functionality to manage migration transactions in a database.
 * <p>
 * This class provides the following operations:
 * <ul>
 *     <li>Execute pending migrations</li>
 *     <li>Rollback migrations to a specific version</li>
 *     <li>Get the status of applied and pending migrations</li>
 *     <li>Initialize migration tables</li>
 * </ul>
 * It works with a {@link ConnectionManager} to manage the database connection,
 * a {@link MigrationManager} for handling the migrations, and a {@link ParseSql} for reading
 * SQL migration files.
 */
@Getter
@Slf4j
public class MigrationExecutor {

    private final ConnectionManager connectionManager;
    private final MigrationManager migrationManager;
    private final ParseSql parseSql;
    private final Connection connection;
    private final Properties properties;
    private static final String INSERT_NEW_MIGRATION_QUERY = "INSERT INTO applied_migrations (migration_name, applied_at, created_by) VALUES (?, ?, ?)";
    /**
     * Constructs a new {@code MigrationExecutor} with the given {@link ConnectionManager},
     * {@link MigrationManager}, and {@link Properties}.
     *
     * @param connectionManager the manager for handling database connections
     * @param migrationManager the manager for handling migrations
     * @param properties the properties used for configuration
     */
    public MigrationExecutor(ConnectionManager connectionManager,
                             MigrationManager migrationManager,
                             Properties properties) {
        this.connectionManager = connectionManager;
        this.migrationManager = migrationManager;
        this.properties = properties;

        this.parseSql = new ParseSql();
        this.connection = connectionManager.getConnection();
    }
    /**
     * Executes all pending migrations in the database. The migrations are executed within
     * a single transaction. If any migration fails, the transaction is rolled back and the
     * method returns {@code false}.
     * <p>
     * A lock is acquired during migration execution to prevent conflicts with other processes
     * modifying the database.
     * </p>
     *
     * @return {@code true} if all migrations were successfully executed, {@code false} otherwise
     */
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
            return true;

        } catch (SQLException exception) {
            log.error("Error during migrations" + exception.getMessage());
            throw new MigrationException("Error during migrations" + exception.getMessage());
        }finally {
            connectionManager.releaseLock(connection);
        }
    }
    /**
     * Rolls back migrations to the specified version. The rollback is executed within
     * a single transaction. If any error occurs, the transaction is rolled back and the
     * method terminates early.
     * <p>
     * A lock is acquired during rollback to prevent conflicts with other processes
     * modifying the database.
     * </p>
     *
     * @param version the version to roll back to
     */
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

        } catch (SQLException exception) {
            log.error("Failed to rollback migrations" + exception.getMessage());
            throw new MigrationException("Failed to rollback migrations" + exception.getMessage());
        }finally {
            connectionManager.releaseLock(connection);
        }
    }
    /**
     * Retrieves the status of applied and pending migrations, logging the names of each.
     */
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
        if(sqlCommand == null){
            log.error("SQL command is empty");
            return false;
        }
        log.info("Executing migration: {}", migrationFile.getName());
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCommand)) {
            preparedStatement.execute();
            return true;
        } catch (SQLException e) {
            log.error("Failed to execute migration {}: {}", migrationFile.getName(), e.getMessage());
            return false;
        }
    }

    private void commitTransaction(Connection connection) throws SQLException {
        try {
            connection.commit();
            log.info("Transaction committed successfully.");
        } catch (SQLException e) {
            connection.rollback();
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
