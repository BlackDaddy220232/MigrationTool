package org.example;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import static org.example.PropertiesUtils.loadProperties;

public class MigrationTool {
    private static String propetriesFilePath = "D:\\Internship\\MigrationTool\\src\\main\\resources\\application.properties";
    static ConnectionManager connectionManager = new ConnectionManager(propetriesFilePath);
    static MigrationFileReader migrationFileReader = new MigrationFileReader("D:\\Internship\\MigrationTool\\src\\main\\resources\\migrations");
    private static MigrationManager migrationManager = new MigrationManager(connectionManager,migrationFileReader);
    private static ParseSql parseSql = new ParseSql();

    public static void main(String[] args) throws InterruptedException, SQLException {
        List<File> fileLinkedList = migrationFileReader.getMigrationFiles();
        fileLinkedList.forEach(file -> System.out.println(file.getName()));
        Connection connection = connectionManager.getConnection();
        System.out.println(migrationManager.getAppliedMigrations());
        System.out.println(migrationManager.getPendingMigrations().getLast().getName());
        String sqlCommand = parseSql.sqlConverter(migrationManager.getPendingMigrations().getLast());
        if(connectionManager.acquireLock(connection)) {
            PreparedStatement preparedStatement = connection.prepareStatement(sqlCommand);
            preparedStatement.execute();
        }
        connectionManager.releaseLock(connection);

    }
}