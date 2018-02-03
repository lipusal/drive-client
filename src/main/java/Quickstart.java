import client.LocalDirectoryWatcher;
import client.download.ChangesTracker;
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
import com.google.api.services.drive.model.FileList;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Quickstart {
    /**
     * Application name.
     */
    private static final String APPLICATION_NAME =
            "JuanLiPuma-DriveClient/1.0";

    /**
     * Root of locally-synchronized files
     */
    private static final Path ROOT = Paths.get("D:\\Users\\juan_\\Desktop\\drive-test-root");

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
    public static Credential authorize() throws IOException {
        // Load client secrets.
        InputStream in =
                Quickstart.class.getResourceAsStream("/client_secret.json");
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
        System.out.println(
                "Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
        return credential;
    }

    /**
     * Build and return an authorized Drive client service.
     *
     * @return an authorized Drive client service
     * @throws IOException
     */
    public static Drive getDriveService() throws IOException {
        Credential credential = authorize();
        return new Drive.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        // Build a new authorized API client service.
        Drive driveService = getDriveService();

        /* *********************************************************************************************************
         *                                  SYNC LOCAL AND REMOTE FILE FILESYSTEMS
         * ********************************************************************************************************/
//            new FilesystemMapper(Paths.get("."), drive, Paths.get("remote_structure.json")).crawlRemote();
//            System.out.println(drive.files().list().setFields("*").setQ("mimeType=application/vnd.google-apps.folder").execute());
//            System.out.println(drive.files().create(new File().setName("hola.txt").setMimeType("text/plain").setParents(Collections.singletonList("root"))).setFields("id, parents").execute());
        FileList rootDirs = driveService.files().list()
                .setQ("'root' in parents and mimeType = 'application/vnd.google-apps.folder'")
                .execute();

        if (rootDirs == null || rootDirs.size() == 0) {
            System.out.println("No files found on root.");
        } else {
            System.out.println("Root files: ");
            rootDirs.getFiles().forEach(file ->
                    System.out.printf("%s (%s)\n", file.getName(), file.getId())
            );
        }

        /* *********************************************************************************************************
         *                                  PERIODICALLY CHECK FOR REMOTE CHANGES
         * ********************************************************************************************************/
        ChangesTracker changesTracker = new ChangesTracker(driveService);
        new ScheduledThreadPoolExecutor(1).scheduleWithFixedDelay(() -> {
            try {
                ChangesTracker.defaultChangeConsumer.accept(changesTracker.call());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 1, TimeUnit.MINUTES);

        /* *********************************************************************************************************
         *                                  WATCH AND UPLOAD LOCAL CHANGES
         * ********************************************************************************************************/
        LocalDirectoryWatcher watcher = new LocalDirectoryWatcher(ROOT);
        watcher.setCreatedListener(changedFile -> {
            try {
                File createdFile = new FileCreator(driveService, changedFile).call();
                System.out.format("Successfully created file %s", createdFile.getId());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread watcherThread = new Thread(watcher);
        watcherThread.start();
        watcherThread.join();   // Wait for watcher to finish (ie. run forever)
    }
}
