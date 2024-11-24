package org.example.db;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.file.ParseSql;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Slf4j
public class MigrationExecutor {

    private ParseSql parseSql = new ParseSql();

    public boolean executeMigrations(List<File> migrationFiles, Connection connection) {
        log.info("Starting migration execution...");
        try {
            connection.setAutoCommit(false);
            for (File migrationFile : migrationFiles) {
                if (!executeSingleMigration(migrationFile, connection)) {
                    rollbackTransaction(connection);
                    return false;
                }
            }
            commitTransaction(connection);
            log.info("All migrations successfully applied!");
            return true;

        } catch (SQLException e) {
            handleExecutionError(connection, e);
            return false;
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
}
