package client;

import client.discovery.DepthLimitedRemoteDiscoverer;
import client.discovery.NaiveRemoteDiscoverer;
import client.discovery.filtering.NoFilterStrategy;
import client.discovery.mapping.MapIfNotAlreadyMappedStrategy;
import client.discovery.sync.SyncIfNotIgnoredStrategy;
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
    private FileIgnorer globalIgnorer;
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
        // 3) Instance global file ignorer, which is relative to global root (defined in global mapper)
        globalIgnorer = new FileIgnorer(globalMapper.getLocalRoot(), getGlobalIgnoreRules());

        // 4) Crawl entire remote if necessary
        boolean crawled = false;
        if (configuration.get("crawl").getAsBoolean()) {
            try {
                globalMapper.crawlRemoteDirs();
                configuration.add("crawl", new JsonPrimitive(false));
                crawled = true;
            } catch (IOException e) {
                System.err.println("Error crawling remote filesystem (crawl occurs during first run or when configured to do so). Aborting.");
                logger.error("Error crawling remote: {}", e);
                System.exit(1);
            }
        }

        // 5) Set which remote directories to sync
        setSyncedRemoteDirs(driveService, !crawled);

        // 6) Save config
        try {
            logger.debug("Saving updated config to {}", CONFIG_FILE);
            writeToFile();
        } catch (Exception e) {
            System.err.println("Couldn't save configuration, exiting.");
            logger.error("Couldn't save config", e);
            System.exit(1);
        }

        // 7) Persist map changes because we probably made at least some new mappings
        try {
            globalMapper.writeToFile();
        } catch (IOException e) {
            System.err.println("Couldn't save configuration. Aborting.");
            logger.error("Couldn't write map to file after configuring", e);
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
     * @param updateFirst   Whether root and synced directories should be updated (ie. fetched from remote) prior to selection.
     */
    private void setSyncedRemoteDirs(Drive driveService, boolean updateFirst) {
        System.out.println("Loading your Drive folders...");

        RemoteExplorer remoteExplorer = new RemoteExplorer(driveService);
        if (updateFirst) {
            try {
                updateRootDirectories(remoteExplorer, driveService);
                updateSyncedDirectories(remoteExplorer, driveService);
            } catch (IOException e) {
                System.err.println("Couldn't get your Drive folders: " + e.getMessage() + ". Exiting.");
                logger.error("Couldn't crawl remote filesystem in configuration", e);
                System.exit(1);
            }
        }

        boolean done = false;
        Scanner scanner = new Scanner(System.in);
        DirectoryChooser chooser = new DirectoryChooser(globalMapper.getRootMapping());
        do {
            System.out.println("Selected folders:");
            System.out.println(chooser.tree());
            boolean validEntry;
            do {
                System.out.print("Enter a number to toggle whether to sync the corresponding directory (q to end): ");    // TODO: Allow ranges, etc.
                String entry = scanner.nextLine();
                switch (entry) {
                    case "q":
                        done = true;
                        validEntry = true;
                        break;
//                    case "*":
//                        validEntry = true;
//                        break;
                    default:
                        try {
                            DirectoryMapping matchedDir = chooser.mappingFromInput(entry);
                            boolean isSynced = !matchedDir.isSynced();
                            //noinspection ConstantConditions,ConstantIfStatement FOR NOW assume all directories up to date (ie. no need to discover remote)
                            if (false /*isSynced && !matchedDir.areSubdirsUpToDate()*/) {
                                int maxDepth = 2;
                                System.out.println("Getting subdirectories of " + matchedDir.getName() + "...");
                                logger.debug("Getting subdirectories of {} up to {} in depth", matchedDir.getName(), maxDepth);
                                // TODO NOW decide whether to use regexes or blobs
                                new DepthLimitedRemoteDiscoverer(
                                        driveService,
                                        matchedDir,
                                        new SyncIfNotIgnoredStrategy(),
                                        maxDepth
                                ).setDirectoryConsumer(file -> {
                                    // The sync strategy is only applied to new mappings (and using an AlwaysMapStrategy
                                    // currently causes an exception, try it and you will see why), so we forcefully set
                                    // the sync flag of all directories here. This will also cover already-mapped dirs.
                                    DirectoryMapping mapping = Optional.ofNullable(globalMapper.getMapping(file.getId())).orElseThrow(() -> new IllegalStateException("Set to map but received a file with no mapping"));
                                    mapping.setSync(!globalIgnorer.isIgnored(mapping.getLocalPath()));
                                }).discover();

//                                globalMapper.discover(matchedDir, isSynced, globalIgnorer, 2, null);
                                matchedDir.setSync(true);
                            } else {
                                deepSetSynced(matchedDir, isSynced);
                            }
                            validEntry = true;
                        } catch (NoSuchElementException | NumberFormatException e) {
                            System.out.printf("Invalid entry: %s\n", e.getMessage());
                            validEntry = false;
                        } catch (IOException e) {
                            logger.error("Couldn't deep get subdirectories of selected directory, invalidating entry", e);
                            System.out.println("Couldn't get subdirectories of selected directory, please try again");
                            validEntry = false;
                        }
                        break;
                }
            } while (!validEntry);
        } while (!done);

        logger.debug("Marked {} directories for sync", getSyncedDirIds().size());
    }

    /**
     * Fetch the latest info about root directories, mapping any unmapped directories.
     *
     * @param remoteExplorer    Remote explorer to get root directories with.
     * @throws IOException      On I/O errors when fetching root dirs.
     */
    private void updateRootDirectories(RemoteExplorer remoteExplorer, Drive drive) throws IOException {
        // Fetch latest info about root directories
        List<String> syncedDirIds = getSyncedDirIds();
        new DepthLimitedRemoteDiscoverer(
                drive,
                globalMapper.getRootMapping(),
                new MapIfNotAlreadyMappedStrategy(),
                (File remote, DirectoryMapping parent) -> syncedDirIds.contains(remote.getId()),
                new NoFilterStrategy(),
                1).discover();

        // Simpler alternative without a discoverer TODO decide which to keep, remove the unused parameter
//        List<File> rootDirs = remoteExplorer.getSubdirs(getRemoteRoot());

        // Map any new root directories that were not previously mapped
//        rootDirs.forEach(rootDir -> {
//            String remoteId = rootDir.getId();
//            if (!globalMapper.isMapped(remoteId)) {
//                Path localPath = Paths.get(getLocalRoot().toString(), rootDir.getName());
//                boolean synced = syncedDirIds.contains(remoteId);
//                globalMapper.mapSubdir(localPath, remoteId, synced, globalMapper.getRootMapping());
//            }
//        });
    }

    /**
     * Loop over {@link #getSyncedDirIds()}, walking down the remote filesystem for every synced directory. While walking
     * down, map any unmapped subdirectories.  Sets all synced directories (and all their subdirectories) with subdirs
     * up to date (ie. calling {@link DirectoryMapping#areSubdirsUpToDate()} will return {@code true}).
     *
     * @param remoteExplorer    Remote explorer.
     * @param drive             Drive service.
     * @throws IOException      On I/O errors when exploring.
     */
    private void updateSyncedDirectories(RemoteExplorer remoteExplorer, Drive drive) throws IOException {
        JsonArray syncedDirsRaw = configuration.getAsJsonArray("sync");
        for (String dirId : getSyncedDirIds()) {
            // Handle edge case
            if (!globalMapper.isMapped(dirId)) {
                // This happens when we sync a non-root directory, and we delete the mappings file. Get the path to root first.
                logger.warn("Directory " + dirId + " is marked for sync but not mapped. Assuming this is because it's a non-root directory. Building path to directory and mapping.");
                List<File> missingDirs = remoteExplorer.getPathToRoot(dirId, getRemoteRoot());
                for (int i = missingDirs.size() - 1; i >= 0; i--) {
                    DirectoryMapping parent = i == missingDirs.size() - 1 ? globalMapper.getRootMapping() : globalMapper.getMapping(missingDirs.get(i + 1).getId());
                    File currentDir = missingDirs.get(i);
                    globalMapper.mapSubdir(Paths.get(parent.getLocalPath().toString(), currentDir.getName()), currentDir.getId(), true, parent);
                }
            }

            DirectoryMapping syncedDir = globalMapper.getMapping(dirId);
            if (!syncedDir.areSubdirsUpToDate()) {
                // Walk down directory tree, mapping any new directories we come across
                new NaiveRemoteDiscoverer(
                        drive,
                        syncedDir,
                        new SyncIfNotIgnoredStrategy()
                ).setDirectoryConsumer(file -> {
                    DirectoryMapping mapping = Optional.ofNullable(globalMapper.getMapping(file.getId())).orElseThrow(() -> new IllegalStateException("Set to map if not already mapped but received a File with no mapping"));
                    mapping.setSubdirsUpToDate(true);   // Since we will eventually go all the way down the tree
                    // Add or remove subdirs from synced list as necessary
                    JsonElement elementJson = new JsonPrimitive(mapping.getRemoteId());
                    if (mapping.isSynced()) {
                        if (!syncedDirsRaw.contains(elementJson)) {
                            syncedDirsRaw.add(elementJson);
                        }
                    } else {
                        syncedDirsRaw.remove(elementJson); // Idempotent, no need to check if contains
                    }
                }).discover();

                // Simpler alternative without a discoverer TODO decide which to keep, remove the unused parameter
//                remoteExplorer.deepGetSubdirs(syncedDir.getRemoteId(), fileFileSimpleEntry -> {
//                    File remoteSubdir = fileFileSimpleEntry.getValue();
//                    DirectoryMapping parentMapping = globalMapper.getMapping(fileFileSimpleEntry.getKey().getId()),
//                            mapping = globalMapper.getMapping(remoteSubdir.getId());
//                    if (mapping == null) {
//                        // Unmapped directory, map (set synced to true)
//                        mapping = new DirectoryMapping(remoteSubdir.getId(), Paths.get(parentMapping.getLocalPath().toString(), remoteSubdir.getName()), true);
//                        globalMapper.mapSubdir(mapping, parentMapping);
//                    }
//                    mapping.setSubdirsUpToDate(true);   // Since we will eventually go all the way down the tree
//                    // Add or remove subdirs from synced list as necessary
//                    JsonElement elementJson = new JsonPrimitive(mapping.getRemoteId());
//                    if (mapping.isSynced()) {
//                        if (!syncedDirsRaw.contains(elementJson)) {
//                            syncedDirsRaw.add(elementJson);
//                        }
//                    } else {
//                        syncedDirsRaw.remove(elementJson); // Idempotent, no need to check if contains
//                    }
//                });
                syncedDir.setSubdirsUpToDate(true);
            }
        }
    }

    public JsonObject getConfig() {
        return configuration;
    }

    public List<String> getSyncedDirIds() {
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

    public FilesystemMapper getGlobalMapper() {
        return globalMapper;
    }

    public void setGlobalMapper(FilesystemMapper globalMapper) {
        if (this.globalMapper != null) {
            throw new IllegalStateException("Global mapper is already set, can't change once set");
        } else {
            this.globalMapper = globalMapper;
        }
    }

    public FileIgnorer getGlobalIgnorer() {
        return globalIgnorer;
    }

    public List<String> getGlobalIgnoreRules() {
        return Util.streamFromIterator(configuration.getAsJsonArray("ignore").iterator()).map(JsonElement::getAsString).collect(Collectors.toList());
    }

    private void setRemoteRootId(String remoteRootId) {
        configuration.add("remoteRoot", new JsonPrimitive(remoteRootId));
    }

    private String fetchRemoteRootId(Drive driveService) throws IOException {
        return new RemoteExplorer(driveService).findById("root").getId();
    }

    private void writeToFile() throws IOException {
        Writer configWriter = new FileWriter(CONFIG_FILE.toFile());
        new GsonBuilder().setPrettyPrinting().create().toJson(configuration, configWriter);
        configWriter.close();
    }

    private List<DirectoryMapping> getSyncedMappings() {
        List<DirectoryMapping> result = new LinkedList<>();
        configuration.getAsJsonArray("sync").forEach(remoteId -> result.add(globalMapper.getMapping(remoteId.getAsString())));
        return result;
    }

    /**
     * Set a directory's synced flag. Also set all its mapped subdirectories' flags to the same as the parent directory.
     * For every directory ({@code mapping} and its subdirs), add or remove them from the configured synced directories
     * as necessary.
     * <strong>NOTE:</strong> Assumes {@code mapping.areSubdirsUpToDate()}. Ie. will only set the flag for mapped
     * directories.
     *
     *  @param mapping  The mapping of the directory whose flag to toggle.
     * @param synced    Whether the mapping is synced.
     */
    private void deepSetSynced(DirectoryMapping mapping, boolean synced) {
        JsonArray syncedDirs = configuration.getAsJsonArray("sync");
        mapping.deepWalkSelfAndSubdirs(m2 -> {
            m2.setSync(synced);
            if (synced) {
                JsonElement elementJson = new JsonPrimitive(m2.getRemoteId());
                if (!syncedDirs.contains(elementJson)) {
                    syncedDirs.add(elementJson);
                }
            } else {
                syncedDirs.remove(new JsonPrimitive(m2.getRemoteId())); // Idempotent, no need to check whether exists
            }
        });
    }
}
