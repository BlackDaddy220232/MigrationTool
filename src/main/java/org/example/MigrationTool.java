package org.example;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import static org.example.PropertiesUtils.loadProperties;

public class MigrationTool {
    static ConnectionManager connectionManager = ConnectionManager.getInstance();

    static MigrationFileReader migrationFileReader = new MigrationFileReader("D:\\Internship\\MigrationTool\\src\\main\\resources\\migrations");
    public static void main(String[] args) throws IOException {
        List<File> fileLinkedList = migrationFileReader.getMigrationFiles();
        fileLinkedList.forEach(file -> System.out.println(file.getName()));
        //Properties properties = loadProperties("D:\\Internship\\MigrationTool\\src\\main\\resources\\application.properties");
        //System.out.println(properties.getProperty("spring.datasource.password"));
        connectionManager.startConnection();


    }
}