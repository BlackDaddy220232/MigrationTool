package org.example;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class MigrationTool {
    static MigrationFileReader migrationFileReader = new MigrationFileReader("D:\\Internship\\MigrationTool\\src\\main\\resources\\migrations");
    public static void main(String[] args) {
        List<File> fileLinkedList = migrationFileReader.getMigrationFiles();
        fileLinkedList.forEach(file -> System.out.println(file.getName()));
    }
}