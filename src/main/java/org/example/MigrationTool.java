package org.example;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import static org.example.PropertiesUtils.loadProperties;

public class MigrationTool {
    private static String propetriesFilePath = "D:\\Internship\\MigrationTool\\src\\main\\resources\\application.properties";
    static ConnectionManager connectionManager = new ConnectionManager(propetriesFilePath);
    static MigrationFileReader migrationFileReader = new MigrationFileReader("D:\\Internship\\MigrationTool\\src\\main\\resources\\migrations");
    public static void main(String[] args){
        List<File> fileLinkedList = migrationFileReader.getMigrationFiles();
        fileLinkedList.forEach(file -> System.out.println(file.getName()));
        Connection connection = connectionManager.getConnection();
        if(connectionManager.acquireLock(connection)){
            System.out.println("Sosi");
        }


        connectionManager.releaseLock(connection);



    }
}