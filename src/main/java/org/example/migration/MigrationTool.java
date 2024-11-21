package org.example.migration;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.db.ConnectionManager;
import org.example.db.MigrationExecutor;
import org.example.db.MigrationManager;
import org.example.file.MigrationFileReader;

import java.io.File;
import java.sql.*;
import java.util.List;
import java.util.Properties;

import static org.example.utils.PropertiesUtils.loadProperties;
@NoArgsConstructor
@Slf4j
public class MigrationTool {
    private static final String propetriesFilePath = "D:\\Internship\\MigrationTool\\src\\main\\resources\\application.properties";
    private static final Properties properties = loadProperties(propetriesFilePath);
    private static final ConnectionManager connectionManager = new ConnectionManager(properties);
    private static final MigrationFileReader migrationFileReader = new MigrationFileReader("D:\\Internship\\MigrationTool\\src\\main\\resources\\migrations");
    private static final MigrationManager migrationManager = new MigrationManager(migrationFileReader,properties);
    private static final MigrationExecutor migrationExecutor = new MigrationExecutor();
    Connection connection;
    List<File> filesForMigration;

    public boolean doMigrations() throws InterruptedException {
            connection = connectionManager.getConnection();
            filesForMigration = migrationManager.getPendingMigrations(connection);
            if (filesForMigration.isEmpty()) {
                log.info("No migration to load");
                return true;
            }
            boolean lockAcquired = false;
            while (!lockAcquired) {
                lockAcquired = connectionManager.acquireLock(connection);
                if (!lockAcquired) {
                    log.info("Lock not acquired. Retrying...");
                    Thread.sleep(5000);
                }
            }
            boolean migrationsSuccessful = migrationExecutor.executeMigrations(filesForMigration, connection);
            if (migrationsSuccessful) {
                log.info("Migrations have been successfully loaded");
                migrationManager.registerMigrations(filesForMigration, connection);
                connectionManager.releaseLock(connection);
                return true;
            } else {
                log.error("Migrations failed to load.");
                connectionManager.releaseLock(connection);
                return false;
            }
        }
    }
