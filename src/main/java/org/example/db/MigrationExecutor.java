package org.example.db;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.file.ParseSql;

import java.io.File;
import java.sql.*;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Slf4j
public class MigrationExecutor {
    private ParseSql parseSqe = new ParseSql();

    public boolean executeMigrations(List<File> migrationsFile, Connection connection) {
        log.info("Starting migration execution...");

        try {
            connection.setAutoCommit(false);
            for (File migrationFile : migrationsFile) {
                String sqlCommand = parseSqe.sqlConverter(migrationFile);
                try (PreparedStatement preparedStatement = connection.prepareStatement(sqlCommand)) {
                    log.info("Executing migration: {}", migrationFile.getName());
                    preparedStatement.execute();
                } catch (SQLException e) {
                    log.error("Failed to execute migration {}: {}", migrationFile.getName(), e.getMessage(), e);
                    connection.rollback();
                    return false;
                }
            }
            connection.commit();
            log.info("All migrations successfully applied!");
            return true;
        } catch (SQLException e) {
            log.error("Error during migration execution: {}", e.getMessage(), e);
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                throw new RuntimeException("Rollback failed:" + rollbackEx.getMessage());
            }
            return false;
        }
    }
}
