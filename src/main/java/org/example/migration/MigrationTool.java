package org.example.migration;

import lombok.extern.slf4j.Slf4j;
import org.example.db.ConnectionManager;
import org.example.executor.MigrationExecutor;
import org.example.db.MigrationManager;
import org.example.utils.PropertiesUtils;

import java.util.Properties;
import java.util.Scanner;
/**
 * The MigrationTool class is the entry point for managing database migrations and rollbacks.
 * It provides a console-based interface for executing migrations, rolling back to a specific version,
 * and retrieving the status of applied migrations.
 * <p>
 * This tool uses {@link ConnectionManager}, {@link MigrationManager}, and {@link MigrationExecutor}
 * to handle database interactions and execute migration-related tasks.
 * </p>
 * <p>
 * The tool operates through a menu-driven interface, allowing users to:
 * <ul>
 *     <li>Run all pending migrations</li>
 *     <li>Rollback migrations to a specific version</li>
 *     <li>View the status of applied migrations</li>
 * </ul>
 * <p>
 */
@Slf4j
public class MigrationTool {
    private final Properties properties = PropertiesUtils.loadProperties("application.properties");
    private final ConnectionManager connectionManager = new ConnectionManager(properties);
    private final MigrationManager migrationManager = new MigrationManager(properties);
    private final MigrationExecutor migrationExecutor = new MigrationExecutor(connectionManager, migrationManager, properties);
    /**
     * Starts the migration tool.
     * Displays a menu-based interface to interact with the user for managing migrations.
     */
    public void tool() {
        migrationExecutor.init();
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                displayMenu();
                int choice = getChoice(scanner);
                switch (choice) {
                    case 1: migrate(); break;
                    case 2: choosingRollbackOption(scanner); break;
                    case 3: status(); break;
                    case 4: return;
                    default: log.warn("Invalid choice, please try again.");
                }
            }
        }
    }

    private void displayMenu() {
        System.out.println("======MENU======");
        System.out.println("1. Run migrations");
        System.out.println("2. Rollback to any version");
        System.out.println("3. Get status of migrations");
        System.out.println("4. Exit");
        System.out.print("Choose your option: ");
    }

    private int getChoice(Scanner scanner) {
        while (!scanner.hasNextInt()) {
            System.out.println("Invalid input. Please enter a number.");
            scanner.next();
        }
        return scanner.nextInt();
    }

    private void migrate() {
        migrationExecutor.executeMigrations();
    }

    private void choosingRollbackOption(Scanner scanner) {
        while (true) {
            System.out.println("Put a version in format (X_X), for example (1_1):");
            String version = scanner.nextLine();
            if (isValidVersionFormat(version)) {
                rollback(version);
                break;
            } else {
                System.out.println("Invalid input format. Please try again.");
            }
        }
    }

    private boolean isValidVersionFormat(String version) {
        return version.matches("^\\d+_\\d+$");
    }

    private void rollback(String version) {
        try {
            migrationExecutor.rollbackMigrations(version);
            log.info("Rollback to version {} completed successfully.", version);
        } catch (Exception e) {
            log.error("Error during rollback to version {}: {}", version, e.getMessage());
        }
    }

    private void status() {
        migrationExecutor.getStatus();
    }
}

