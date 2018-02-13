package client;

import client.download.RemoteExplorer;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Config {
    public static final String DIRECTORY_MIME_TYPE = "application/vnd.google-apps.folder";
    public static final long MAX_DIRECT_DOWNLOAD_SIZE = 5000000;    // 5MB TODO move this to config file
    public static final int MAX_PAGE_SIZE = 1000;
    public static final int TIMEOUT = 1000 * 60;    // 1 minute

    private static Config instance;

    private static final Path CONFIG_FILE = Paths.get("config.json").toAbsolutePath();
    private static final Path DEFAULT_CONFIG_FILE = Paths.get("config.default.json").toAbsolutePath();
    private JsonObject configuration;
    private FilesystemMapper globalMapper;
    private Logger logger = LoggerFactory.getLogger(getClass());

    public static Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    private Config() {
        // Load default config, can't fail
        try (Reader configReader = new FileReader(DEFAULT_CONFIG_FILE.toFile())) {
            this.configuration = new Gson().fromJson(configReader, JsonObject.class);
        } catch (Exception e) {
            logger.error("Couldn't load default configuration file {}: ", DEFAULT_CONFIG_FILE.toString(), e);
            System.exit(1);
        }
        // Load saved config, if any
        try (Reader configReader = new FileReader(CONFIG_FILE.toFile())) {
            this.configuration = new Gson().fromJson(configReader, JsonObject.class);
            logger.debug("Loaded configuration file {}", CONFIG_FILE.toString());
        } catch (FileNotFoundException e) {
            logger.debug("Configuration file {} not found, using default config", CONFIG_FILE.toString());
        } catch (IOException e) {
            logger.warn("Abnormal error loading config file {}, using default config", CONFIG_FILE, e);
        }
    }

    /**
     * Check whether a custom configuration file exists.
     *
     * @return Whether a custom configuration file exists.
     */
    public boolean isConfigured() {
        return Files.exists(CONFIG_FILE);
    }

    public void configure(Drive driveService) {
        System.out.println("\n*************************************************\n");
        System.out.println("Welcome to the configuration!");

        // Get remote root folder ID, necessary for some computations
        if (getRemoteRoot() == null) {
            try {
                logger.debug("Remote root ID not set, fetching from remote");
                setRemoteRootId(fetchRemoteRootId(driveService));
            } catch (IOException e) {
                System.err.println("Couldn't fetch necessary information. Exiting.");
                logger.error("Couldn't get ID of remote root, can't continue", e);
                System.exit(1);
            }
        }
        /* ************************
         *        CONFIGURE
         * ***********************/
        // 1) Set local root
        setLocalRoot();

        // 2) Instance global filesystem mapper to manage remote FS
        if (globalMapper == null) {
            try {
                globalMapper = new FilesystemMapper(getMapFilePath(), driveService);
            } catch (Exception e) {
                System.err.println("Couldn't complete configuration (internal error, this is not your fault). Aborting.");
                logger.error("Couldn't instance global filesystem mapper", e);
                System.exit(1);
            }
        }

        // 3) Set which remote directories to sync
        setSyncedRemoteDirs(driveService);

        // 4) Save config
        try {
            logger.debug("Saving updated config to {}", CONFIG_FILE);
            writeToFile();
        } catch (Exception e) {
            System.err.println("Couldn't save configuration, exiting.");
            logger.error("Couldn't save config", e);
            System.exit(1);
        }

        // 5) Sync global mapper with new synced directories
        try {
            globalMapper.syncWithConfig();
        } catch (IOException e) {
            System.err.println("Couldn't complete configuration (internal error, this is not your fault). Aborting.");
            logger.error("Couldn't sync global filesystem mapper with config", e);
            System.exit(1);
        }

        System.out.println("Configuration complete!");
        System.out.println("\n*************************************************\n");
    }

    private void setLocalRoot() {
        logger.debug("Setting local root");
        Path localRoot = getLocalRoot();
        Path enteredPath = null;
        boolean canSkip = localRoot != null;
        boolean done = false;
        Scanner s = new Scanner(System.in);
        do {
            System.out.println("\nType the absolute path where you would like to sync folders (local root).");
            System.out.printf("Current local root is %s\n", localRoot == null ? "not set" : localRoot);
            if (canSkip) {
                System.out.print("Enter \"q\" to skip and use current local root: ");
            } else {
                System.out.print("Local root: ");
            }
            try {
                enteredPath = Paths.get(s.nextLine());
                if (!enteredPath.isAbsolute()) {
                    throw new InvalidPathException(enteredPath.toString(), "Entered path is not absolute", 0);
                }
                done = true;
            } catch (InvalidPathException e) {
                if (e.getInput().equalsIgnoreCase("q") && canSkip) {
                    enteredPath = localRoot;
                    done = true;
                } else {
                    System.out.printf("\nInvalid path: %s", e.getMessage());
                }
            }
        } while (!done);

        // Local root chosen, update config
        configuration.add("localRoot", new JsonPrimitive(enteredPath.toString()));
        logger.debug("Local root set to {}", enteredPath);
    }

    /**
     * Set which remote directories will be synced.  Connect to Drive, allow the user to pick which folders to sync,
     * and update configuration.
     *
     * @param driveService  The Drive service to fetch remote directories with.
     */
    private void setSyncedRemoteDirs(Drive driveService) {
        System.out.println("Loading your Drive folders...");

        RemoteExplorer remoteExplorer = new RemoteExplorer(driveService);
        try {
//            globalMapper.crawlRemoteDirs();

            // Get latest root directories
            List<File> rootDirs = remoteExplorer.getSubdirs(getRemoteRoot());

            // Take the opportunity to map any new root directories that were not previously mapped
            List<String> syncedDirIds = getSyncedFolderIds();
            rootDirs.forEach(rootDir -> {
                String remoteId = rootDir.getId();
                if (!globalMapper.isMapped(remoteId)) {
                    Path localPath = Paths.get(getLocalRoot().toString(), rootDir.getName());
                    boolean synced = syncedDirIds.contains(remoteId);
                    globalMapper.mapSubdir(localPath, remoteId, globalMapper.getRootMapping());
                    globalMapper.getMapping(remoteId).setSync(synced);
                }
            });

            // Set sync flag for mappings of synced directories
            for (DirectoryMapping syncedDir : getSyncedMappings()) {// TODO: Set all other mappings to sync: false?
                syncedDir.setSync(true);
                // Get updated subdir data of every synced directory (deep)
                remoteExplorer.deepGetSubdirs(syncedDir.getRemoteId(), fileFileSimpleEntry -> {
                    DirectoryMapping parentMapping = globalMapper.getMapping(fileFileSimpleEntry.getKey().getId());
                    File remoteSubdir = fileFileSimpleEntry.getValue();
                    if (!globalMapper.isMapped(remoteSubdir.getId())) {
                        // New remote directory, add to mappings
                        globalMapper.mapSubdir(remoteSubdir.getId(), Paths.get(parentMapping.getLocalPath().toString(), remoteSubdir.getName()), parentMapping);
                        globalMapper.getMapping(remoteSubdir.getId()).setSync(true);
                    }
                });
            }
        } catch (IOException e) {
            System.err.println("Couldn't get your Drive folders: " + e.getMessage() + ". Exiting.");
            logger.error("Couldn't crawl remote filesystem in configuration", e);
            System.exit(1);
        }

        boolean done = false;
        Scanner scanner = new Scanner(System.in);
        DirectoryChooser chooser = new DirectoryChooser(globalMapper.getRootMapping());
        do {
            System.out.println("Selected folders:");
            System.out.println(chooser.toString());
            boolean validEntry;
            int toggledDir = -1;
            do {
                // TODO NOW
                //System.out.format("Enter a number to toggle whether to sync the corresponding directory. Enter \"*\" to toggle all, \"q\" to confirm [%d-%d,*q] ", 1, rootDirs.size());    // TODO: Allow ranges, etc.
                String entry = scanner.nextLine();
                switch (entry) {
                    case "q":
                        done = true;
                        validEntry = true;
                        break;
                    case "*":
                        validEntry = true;
                        break;
                    default:
                        try {
                            toggledDir = Integer.parseInt(entry) - 1;
                            validEntry = false; // TODO NOW toggledDir >= 0 && toggledDir < rootDirs.size();
                        } catch (NumberFormatException e) {
                            validEntry = false;
                        }
                        if (!validEntry) {
                            System.out.println("Invalid entry");
                        }
                        break;
                }
            } while (!validEntry);
            if (!done) {
                // Toggle selected dirs
                // TODO NOW
//                for (int i = 0; i < rootDirs.size(); i++) {
//                    if (toggledDir == -1 || toggledDir == i) {
//                        File selectedDir = rootDirs.get(i);
//                        if (selectedDirs.contains(selectedDir)) {
//                            selectedDirs.remove(selectedDir);
//                        } else {
//                            selectedDirs.add(selectedDir);
//                        }
//                    }
//                }
            }
        } while (!done);

        // TODO NOW
//        logger.debug("Marking {} directories for sync (overwriting any previous configured directories)", selectedDirs.size());
//        JsonArray syncedDirs = new JsonArray(selectedDirs.size());
//        selectedDirs.forEach(file -> syncedDirs.add(file.getId()));
//        configuration.add("sync", syncedDirs);
    }

    public JsonObject getConfig() {
        return configuration;
    }

    public List<String> getSyncedFolderIds() {
        return Util.streamFromIterator(configuration.getAsJsonArray("sync").iterator()).map(JsonElement::getAsString).collect(Collectors.toList());
    }

    /**
     * Gets local root as an absolute path.
     *
     * @return The configured local root, as an absolute path.
     */
    public Path getLocalRoot() {
        JsonElement localPath = configuration.get("localRoot");
        return localPath.isJsonNull() ? null : Paths.get(localPath.getAsString()).toAbsolutePath();
    }

    public Path getMapFilePath() {
        return Paths.get(configuration.get("mapFile").getAsString()).toAbsolutePath();
    }

    public String getRemoteRoot() {
        JsonElement remoteRoot = configuration.get("remoteRoot");
        return (remoteRoot == null || remoteRoot.isJsonNull()) ? null : remoteRoot.getAsString();
    }

    private void setRemoteRootId(String remoteRootId) {
        configuration.add("remoteRoot", new JsonPrimitive(remoteRootId));
    }

    private String fetchRemoteRootId(Drive driveService) throws IOException {
        return new RemoteExplorer(driveService).findById("root").getId();
    }

    private void writeToFile() throws IOException {
        Writer configWriter = new FileWriter(CONFIG_FILE.toFile());
        new Gson().toJson(configuration, configWriter);
        configWriter.close();
    }

    private List<DirectoryMapping> getSyncedMappings() {
        if (!configuration.get("sync").isJsonArray()) {
            return Collections.emptyList();
        }
        List<DirectoryMapping> result = new LinkedList<>();
        configuration.getAsJsonArray("sync").forEach(remoteId -> result.add(globalMapper.getMapping(remoteId.getAsString())));
        return result;
    }
}
