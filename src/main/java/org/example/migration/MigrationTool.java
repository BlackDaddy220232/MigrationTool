package org.example.migration;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.db.ConnectionManager;
import org.example.executor.MigrationExecutor;
import org.example.db.MigrationManager;
import org.example.file.MigrationFileReader;
import org.example.utils.PropertiesUtils;

import java.io.File;
import java.sql.*;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

@NoArgsConstructor
@Slf4j
public class MigrationTool {
    Properties properties = PropertiesUtils.loadProperties("application.properties");
    ConnectionManager connectionManager = new ConnectionManager(properties);

    MigrationManager migrationManager = new MigrationManager(properties);

    MigrationExecutor migrationExecutor = new MigrationExecutor(connectionManager,migrationManager,properties);
    Scanner scanner = new Scanner(System.in);

    public void tool() {
        migrationExecutor.init();
        int choice;
        while (true) {
            System.out.println("======MENU======"); //Using System.out.println only for console interface because it's useless info for logging
            System.out.println("1. Run migrations");
            System.out.println("2. Rollback to any version");
            System.out.println("3. Get status of migrations");
            System.out.print("Choose your option: ");
            if (scanner.hasNextInt()) {
                choice = scanner.nextInt();

                switch (choice) {
                    case 1:
                        migrate();
                        break;
                    case 2:
                        choosingRollbackOption();
                        break;
                    case 3:
                        status();
                        break;
                    default:
                        System.out.println("Wrong choice, please try again.");
                        continue; // Continue to the next iteration of the loop
                }
                break;
            } else {
                System.out.println("Invalid input. Please enter a number.");
                scanner.next(); // Clear the invalid input
            }
        }
        scanner.close();
    }

    public void migrate(){
        migrationExecutor.executeMigrations();
    }
    private void choosingRollbackOption() {
        while (true) { // Loop until a valid input is provided
            scanner.nextLine();
            System.out.println("Put a version in format (X_X), for example (1_1):");
            String version = scanner.nextLine(); // Read the input

            // Check if the input matches the expected format
            if (version.matches("^\\d+_\\d+$")) {
                rollback(version); // Call rollback if the format is valid
                break; // Exit the loop on successful rollback
            } else {
                System.out.println("Invalid input format. Please try again.");
                // The loop continues, prompting the user again
            }
        }
    }
    public void rollback(String version){migrationExecutor.rollbackMigrations(version);}
    public void status(){migrationExecutor.getStatus();}

}
