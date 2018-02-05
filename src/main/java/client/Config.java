package client;

import client.download.RemoteExplorer;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Config {
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

        setSyncedRemoteFolders(driveService);

        System.out.println("Configuration complete!");
        System.out.println("\n*************************************************\n");
    }

    public void setSyncedRemoteFolders(Drive driveService) {
        System.out.println("Loading your Drive folders...");

        List<File> rootDirs = null;
        try {
            rootDirs = new RemoteExplorer(driveService).getSubdirs("root");
        } catch (IOException e) {
            System.err.println("Couldn't get your remote folders: " + e.getMessage() + ". Exiting.");
            logger.error("Couldn't get remote folders in configuration", e);
            System.exit(1);
        }
        List<File> selectedDirs = new ArrayList<>(rootDirs.size());
        boolean done = false;
        Scanner scanner = new Scanner(System.in);
        do {
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
    }

    public JsonObject getConfig() {
        return configuration;
    }
}
