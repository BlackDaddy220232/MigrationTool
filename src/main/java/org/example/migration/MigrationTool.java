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

import static org.example.utils.PropertiesUtils.loadProperties;
@NoArgsConstructor
@Slf4j
public class MigrationTool {
    Properties properties = PropertiesUtils.loadProperties("src/main/resources/application.properties");
    ConnectionManager connectionManager = new ConnectionManager(properties);

    MigrationManager migrationManager = new MigrationManager(properties);

    MigrationExecutor migrationExecutor = new MigrationExecutor(connectionManager,migrationManager,properties);

    public void migrate(){
        migrationExecutor.executeMigrations();
    }
    public void rollback(){migrationExecutor.rollbackMigrations("1_0");
    }

}
