package client;

import client.download.RemoteExplorer;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class Config {
    private String REMOTE_ROOT_ID;      // TODO: Move this to FilesystemMapper?
    private static Config instance;

    private JsonObject configuration;
    private static final Path CONFIG_FILE = Paths.get("config.json");
    private static final Path DEFAULT_CONFIG_FILE = Paths.get("config.default.json");
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
        } catch (FileNotFoundException e) {
            logger.debug("Config file {} not found, using default config", CONFIG_FILE.toString());
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

    public void configureRemote(Drive driveService) {
        System.out.println("\n*************************************************\n");
        System.out.println("Welcome to the configuration. Let's start by setting which folders you want to synchronize");

        // Get remote root folder ID, necessary for some computations
        if (REMOTE_ROOT_ID == null) {
            try {
                REMOTE_ROOT_ID = new RemoteExplorer(driveService).findById("root").getId();
            } catch (IOException e) {
                System.err.println("Couldn't fetch necessary information. Exiting.");
                logger.error("Couldn't get ID of remote root, can't continue", e);
                System.exit(1);
            }
        }
        // Configure
        setSyncedRemoteFolders(driveService);

        System.out.println("Configuration complete!");
        System.out.println("\n*************************************************\n");
    }

    public void setSyncedRemoteFolders(Drive driveService) {
        System.out.println("Loading your Drive folders...");

        List<File> rootDirs = null;
        try {
            rootDirs = new RemoteExplorer(driveService).getSubdirs(REMOTE_ROOT_ID);
        } catch (IOException e) {
            System.err.println("Couldn't get your Drive folders: " + e.getMessage() + ". Exiting.");
            logger.error("Couldn't get remote folders in configuration", e);
            System.exit(1);
        }
        List<File> selectedDirs = getSyncedFolders(rootDirs);
        boolean done = false;
        Scanner scanner = new Scanner(System.in);
        do {
            // TODO: Support subdir management
            System.out.println("Selected folders:");
            for (int i = 0; i < rootDirs.size(); i++) {
                File currentDir = rootDirs.get(i);
                System.out.printf("[%s] %d - %s\n", selectedDirs.contains(currentDir) ? "X" : "", i+1, currentDir.getName());
            }
            boolean validEntry;
            int toggledDir = -1;
            do {
                System.out.format("Enter a number to toggle whether to sync the corresponding directory. Enter \"*\" to toggle all, \"q\" to confirm [%d-%d,*q] ", 1, rootDirs.size());    // TODO: Allow ranges, etc.
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
                            validEntry = toggledDir >= 0 && toggledDir < rootDirs.size();
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
                for (int i = 0; i < rootDirs.size(); i++) {
                    if (toggledDir == -1 || toggledDir == i) {
                        File selectedDir = rootDirs.get(i);
                        if (selectedDirs.contains(selectedDir)) {
                            selectedDirs.remove(selectedDir);
                        } else {
                            selectedDirs.add(selectedDir);
                        }
                    }
                }
            }
        } while (!done);

        logger.debug("Marking {} directories for sync (overwriting any previous configured directories)", selectedDirs.size());
        JsonArray syncedDirs = new JsonArray(selectedDirs.size());
        selectedDirs.forEach(file -> {
            syncedDirs.add(file.getId());
        });
        configuration.add("sync", syncedDirs);
        // Write to file
        try(Writer configWriter = new FileWriter(CONFIG_FILE.toFile())) {
            new Gson().toJson(configuration, configWriter);
        } catch (Exception e) {
            System.err.println("Couldn't save configuration, exiting.");
            logger.error("Couldn't save synced directories", e);
            System.exit(1);
        }

        // Update maps file
        logger.debug("Updating maps file");
        try {
            buildMap(selectedDirs, Paths.get(configuration.get("mapsFile").getAsString()));
        } catch (IOException e) {
            System.err.println("Couldn't save configuration, exiting.");
            logger.error("Couldn't update maps file", e);
            System.exit(1);
        }
    }

    public JsonObject getConfig() {
        return configuration;
    }

    private List<File> getSyncedFolders(List<File> rootDirs) {
        if (!configuration.get("sync").isJsonArray()) {
            return Collections.emptyList();
        }
        List<File> result = new ArrayList<>(rootDirs.size());
        rootDirs.forEach(dir -> {
            if (configuration.getAsJsonArray("sync").contains(new JsonPrimitive(dir.getId()))) {
                result.add(dir);
            }
        });
        return result;
    }

    /**
     * Build a map, compatible with {@link FilesystemMapper}, based on directories selected in {@link #configureRemote(Drive)}.
     *
     * @param dirs          The directories to build a map for.
     * @param outputFile    Where to output the file
     * @throws IOException  See {@link Gson#toJson(JsonElement, Appendable)}
     */
    private void buildMap(List<File> dirs, Path outputFile) throws IOException {
        // TODO: Rather than overwriting maps file, merge it with already-existing one, if present
        JsonObject result = new JsonObject();
        result.add("root", new JsonPrimitive(REMOTE_ROOT_ID));
        result.add(REMOTE_ROOT_ID, mapEntry("root", Paths.get("TODO"), true));
        buildMapEntryRecursive(REMOTE_ROOT_ID, new ArrayList<>(dirs), result);

        Writer w = new FileWriter(outputFile.toAbsolutePath().toFile());
        new Gson().toJson(result, w);
        w.close();
    }

    private void buildMapEntryRecursive(String currentParentId, List<File> dirs, JsonObject output) {
        // Find all dirs with currentId as parents
//        List<String> idsToRemove = new ArrayList<>(dirs.size());
        // TODO: Use iterator() to go removing dirs as we visit them
        dirs.stream()
                .filter(file -> file.getParents().contains(currentParentId))
                // Add them to resulting object
                .forEach(file -> {
                    output.add(file.getId(), mapEntry(file.getName(), null, true, currentParentId));
//                    idsToRemove.add(file.getId());
                    buildMapEntryRecursive(file.getId(), dirs, output);
                });
    }

    /**
     * Dynamically build a JsonObject with the shape of an entry as defined in the JSON declared in {@link FilesystemMapper#DEFAULT_MAPS_FILE}.
     */
    private JsonObject mapEntry(String remoteName, Path localPath, boolean sync, String... parents) {
        JsonObject result = new JsonObject();
        result.add("remoteName", remoteName == null ? JsonNull.INSTANCE : new JsonPrimitive(remoteName));
        JsonArray parentsAry = new JsonArray(parents.length);
        for(String parent : parents) {
            parentsAry.add(parent);
        }
        result.add("parents", parentsAry);
        result.add("localPath", localPath == null ? JsonNull.INSTANCE : new JsonPrimitive(localPath.toString()));
        result.add("sync", new JsonPrimitive(sync));

        return result;
    }

    // TODO: Add localRoot to config?
}
