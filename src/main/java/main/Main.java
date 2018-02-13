package main;

import client.Config;
import client.DirectorySyncer;
import client.FilesystemMapper;
import client.LocalDirectoryWatcher;
import client.upload.FileCreator;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;

public class Main {
    /**
     * Application name.
     */
    private static final String APPLICATION_NAME =
            "JuanLiPuma-DriveClient/1.0";

    /**
     * Logger
     */
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Directory to store user credentials for this application.
     */
    private static final java.io.File DATA_STORE_DIR = new java.io.File(
            System.getProperty("user.home"), ".credentials/drive-client");

    /**
     * Global instance of the {@link FileDataStoreFactory}.
     */
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY =
            JacksonFactory.getDefaultInstance();

    /**
     * Global instance of the HTTP transport.
     */
    private static HttpTransport HTTP_TRANSPORT;

    /**
     * Global instance of the scopes required by this quickstart.
     * <p>
     * If modifying these scopes, delete your previously saved credentials
     * at {@link #DATA_STORE_DIR}.
     */
    private static final List<String> SCOPES =
            Collections.singletonList(DriveScopes.DRIVE);

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Creates an authorized Credential object.
     *
     * @return an authorized Credential object.
     * @throws IOException
     */
    private static Credential authorize() throws IOException {
        // Load client secrets.
        InputStream in =
                Main.class.getResourceAsStream("/client_secret.json");
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                        .setDataStoreFactory(DATA_STORE_FACTORY)
                        .setAccessType("offline")
                        .build();
        Credential credential = new AuthorizationCodeInstalledApp(
                flow, new LocalServerReceiver()).authorize("user");
        logger.debug("Credentials saved to {}", DATA_STORE_DIR.getAbsolutePath());
        return credential;
    }

    /**
     * Build and return an authorized Drive client service.
     *
     * @return an authorized Drive client service
     * @throws IOException
     */
    private static Drive getDriveService() throws IOException {
        Credential credential = authorize();
        return new Drive.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                // Increase timeout, https://stackoverflow.com/a/23717324/2333689
                .setHttpRequestInitializer(request -> {
                    credential.initialize(request);
                    request.setConnectTimeout(Config.TIMEOUT);
                    request.setReadTimeout(Config.TIMEOUT);
                })
                .build();
    }

    public static void main(String[] args) throws Exception {
        logger.debug("Starting up...");

        // Build a new authorized API client service.
        Drive driveService = getDriveService();

        boolean runConfig;
        if (!Config.getInstance().isConfigured()) {
            System.out.print("Configuration file not found. ");
            runConfig = true;
        } else {
            System.out.print("Enter anything if you would like to run the configuration. Waiting up to 5 seconds... ");
            try {
                runConfig = Executors.newFixedThreadPool(1).submit(() -> {
                    Scanner scanner = new Scanner(System.in);
                    return scanner.nextLine() != null;   // Anything will go
                }).get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                runConfig = false;
            }
        }
        if (runConfig) {
            System.out.println("Running configuration...");
            Config.getInstance().configure(driveService);
        } else {
            System.out.println("Skipping configuration");
        }

        /* *********************************************************************************************************
         *                                  SYNC LOCAL AND REMOTE FILE FILESYSTEMS
         * ********************************************************************************************************/
        // Mappings of local folders <=> remote folders
        FilesystemMapper globalMapper;
        if (!runConfig) {
            // If configuration is not run, global mapper is not instanced.
            globalMapper = new FilesystemMapper(Config.getInstance().getMapFilePath(), driveService);
            Config.getInstance().setGlobalMapper(globalMapper);
        } else {
            globalMapper = Config.getInstance().getGlobalMapper();
        }
        // Sync content
        System.out.println("Syncing...");
        logger.debug("Beginning sync");
        for(String directoryId : Config.getInstance().getSyncedDirIds()) {
            new DirectorySyncer(globalMapper.getMapping(directoryId), driveService, globalMapper).sync();
        }
        logger.debug("Sync complete!");


        /* *********************************************************************************************************
         *                                  WATCH AND UPLOAD LOCAL CHANGES
         * ********************************************************************************************************/
        System.out.println("Watching directories for changes");
        logger.debug("Watching {} for changes", globalMapper.getLocalRoot());
        LocalDirectoryWatcher watcher = new LocalDirectoryWatcher(globalMapper.getLocalRoot()/*, TODO: true*/);
        watcher.setCreatedListener(changedFile -> {
            try {
                File createdFile = new FileCreator(driveService, changedFile).call();
                logger.info("Successfully created file {}", createdFile.getId());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread watcherThread = new Thread(watcher);
        watcherThread.start();
        watcherThread.join();   // Wait for watcher to finish (ie. run forever)
    }
}
