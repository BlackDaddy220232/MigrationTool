package org.example;

import org.example.migration.MigrationTool;

import java.sql.SQLException;

public class Main {
    private static final MigrationTool migrationTool = new MigrationTool();
    public static void main(String[] args) throws InterruptedException, SQLException {
        migrationTool.status();
    }
}
