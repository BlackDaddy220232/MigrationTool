package org.example.db;

import lombok.extern.slf4j.Slf4j;
import org.example.exception.MigrationException;
import org.example.file.MigrationFileReader;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class MigrationManager {

    private static final String FETCH_APPLIED_MIGRATIONS_QUERY = "SELECT migration_name FROM applied_migrations";
    private static final String INSERT_NEW_MIGRATION_QUERY = "INSERT INTO applied_migrations (migration_name, applied_at, created_by) VALUES (?, ?, ?)";

    private MigrationFileReader fileReader;

    private Properties properties;

    public MigrationManager(Properties properties){
        this.properties=properties;
        fileReader = new MigrationFileReader(properties.getProperty("path.migration"));
    }
    /**
     * Fetches applied migrations from the database.
     *
     * @param connection Database connection
     * @return List of applied migration names
     */
    public List<String> getAppliedMigrations(Connection connection) {
        log.info("Fetching applied migrations from the database...");
        try (PreparedStatement ps = connection.prepareStatement(FETCH_APPLIED_MIGRATIONS_QUERY);
             ResultSet rs = ps.executeQuery()) {
            return extractAppliedMigrations(rs);
        } catch (SQLException e) {
            log.error("Failed to fetch applied migrations: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch applied migrations: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches pending migrations by comparing applied migrations and available files.
     *
     * @param connection Database connection
     * @return List of pending migration files
     */
    public List<File> getPendingMigrations(Connection connection) {
        log.info("Fetching pending migrations...");
        List<File> allMigrations = fileReader.getMigrationFiles();
        List<String> appliedMigrations = getAppliedMigrations(connection);

        List<File> pendingMigrations = filterPendingMigrations(allMigrations, appliedMigrations);
        log.info("Found {} pending migrations.", pendingMigrations.size());
        return pendingMigrations;
    }

    public void registerMigrations(List<File> migrationFiles, Connection connection) {
        log.info("Registering migrations in the database...");
        try {
            executeMigrationRegistration(migrationFiles, connection);
        } catch (SQLException e) {
            log.error("Failed to register migrations: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to register migrations: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts applied migrations from the result set.
     */
    private List<String> extractAppliedMigrations(ResultSet rs) throws SQLException {
        List<String> appliedMigrations = new ArrayList<>();
        while (rs.next()) {
            appliedMigrations.add(rs.getString("migration_name"));
        }
        log.info("Successfully fetched {} applied migrations.", appliedMigrations.size());
        return appliedMigrations;
    }

    /**
     * Filters out applied migrations to determine pending migrations.
     */
    private List<File> filterPendingMigrations(List<File> allMigrations, List<String> appliedMigrations) {
        return allMigrations.stream()
                .filter(file -> !appliedMigrations.contains(file.getName()))
                .collect(Collectors.toList());
    }
    private void executeMigrationRegistration(List<File> migrationFiles, Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_NEW_MIGRATION_QUERY)) {
            for (File migrationFile : migrationFiles) {
                addMigrationToBatch(ps, migrationFile);
            }
            ps.executeBatch();
            log.info("Successfully registered {} migrations.", migrationFiles.size());
        } catch (SQLException e) {
            connection.rollback();
            log.warn("Transaction rolled back due to an error.");
            throw new MigrationException("Transaction rolled back due to an error.",e);
        }
    }
    private void addMigrationToBatch(PreparedStatement ps, File migrationFile) throws SQLException {
        String migrationName = migrationFile.getName();
        ps.setString(1, migrationName);
        ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
        ps.setString(3, properties.getProperty("db.username"));
        ps.addBatch();
        log.info("Added migration {} to the batch.", migrationName);
    }


}
