package org.example;

import lombok.Builder;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
@Builder
public class MigrationManager {

    private static final String FETCH_APPLIED_MIGRATIONS_QUERY = "SELECT migration_name FROM applied_migrations";
    private static final String INSERT_NEW_MIGRATION_QUERY = "INSERT INTO applied_migrations (migration_name, applied_at) VALUES (?, ?)";

    private final ConnectionManager connectionManager;
    private final MigrationFileReader fileReader;

    public List<String> getAppliedMigrations() {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(FETCH_APPLIED_MIGRATIONS_QUERY);
             ResultSet rs = ps.executeQuery()) {

            List<String> appliedMigrations = new ArrayList<>();
            while (rs.next()) {
                appliedMigrations.add(rs.getString("migration_name"));
            }
            return appliedMigrations;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch applied migrations: " + e.getMessage(), e);
        }
    }

    public List<File> getPendingMigrations() {
        List<File> allMigrations = fileReader.getMigrationFiles();
        List<String> appliedMigrations = getAppliedMigrations();

        // Возвращаем только новые миграции
        return allMigrations.stream()
                .filter(file -> !appliedMigrations.contains(file.getName()))
                .collect(Collectors.toList());
    }


    public void validateMigrations(List<File> migrations) {
        Pattern pattern = Pattern.compile("^V\\d+__.+\\.sql$");

        for (File migration : migrations) {
            String fileName = migration.getName();

            // Проверка формата имени
            if (!pattern.matcher(fileName).matches()) {
                throw new RuntimeException("Invalid migration file name: " + fileName +
                        ". Expected format: V[VERSION]__[DESCRIPTION].sql");
            }
        }
    }

    public void registerMigration(String migrationName) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(INSERT_NEW_MIGRATION_QUERY)) {

            ps.setString(1, migrationName);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to register migration: " + migrationName, e);
        }
    }
}
