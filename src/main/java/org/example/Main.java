package org.example;

import org.example.migration.MigrationTool;

public class Main {
    private static final MigrationTool migrationTool = new MigrationTool();
    public static void main(String[] args){
        migrationTool.tool();
    }
}
