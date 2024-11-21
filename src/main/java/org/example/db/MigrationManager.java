package org.example.db;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.file.MigrationFileReader;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@AllArgsConstructor
@Slf4j
public class MigrationManager {

    private static final String FETCH_APPLIED_MIGRATIONS_QUERY = "SELECT migration_name FROM applied_migrations";
    private static final String INSERT_NEW_MIGRATION_QUERY = "INSERT INTO applied_migrations (migration_name, applied_at, created_by) VALUES (?, ?, ?)";

    private final MigrationFileReader fileReader;
    private final Properties properties;

    public List<String> getAppliedMigrations(Connection connection) {
        log.info("Fetching applied migrations from the database...");
        try (PreparedStatement ps = connection.prepareStatement(FETCH_APPLIED_MIGRATIONS_QUERY);
             ResultSet rs = ps.executeQuery()) {
            List<String> appliedMigrations = new ArrayList<>();
            while (rs.next()) {
                appliedMigrations.add(rs.getString("migration_name"));
            }
            log.info("Successfully fetched {} applied migrations.", appliedMigrations.size());
            return appliedMigrations;

        } catch (SQLException e) {
            log.error("Failed to fetch applied migrations: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch applied migrations: " + e.getMessage(), e);
        }
    }

    public List<File> getPendingMigrations(Connection connection) {
        log.info("Fetching pending migrations...");
        List<File> allMigrations = fileReader.getMigrationFiles();
        List<String> appliedMigrations = getAppliedMigrations(connection);

        List<File> pendingMigrations = allMigrations.stream()
                .filter(file -> !appliedMigrations.contains(file.getName()))
                .collect(Collectors.toList());

        log.info("Found {} pending migrations.", pendingMigrations.size());
        return pendingMigrations;
    }

    public void registerMigrations(List<File> migrationFiles,Connection connection) {
        log.info("Registering migrations in the database...");
        try (PreparedStatement ps = connection.prepareStatement(INSERT_NEW_MIGRATION_QUERY)) {

            connection.setAutoCommit(false);

            for (File migrationFile : migrationFiles) {
                String migrationName = migrationFile.getName();
                ps.setString(1, migrationName);
                ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                ps.setString(3, properties.getProperty("db.username"));
                ps.addBatch();
                log.info("Added migration {} to the batch.", migrationName);
            }

            ps.executeBatch();
            connection.commit();
            log.info("Successfully registered {} migrations.", migrationFiles.size());

        } catch (SQLException e) {
            log.error("Failed to register migrations: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to register migrations: " + e.getMessage(), e);
        }
    }
}
