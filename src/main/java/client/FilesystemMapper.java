package client;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Folders in Drive are exclusively identified with IDs, and every folder's parents are merely another property of the
 * folder. This class constructs a tree-like structure to map remote folder IDs to local folders.
 */
public class FilesystemMapper {
    private static final Path DEFAULT_MAP_FILE = Paths.get("map.default.json").toAbsolutePath();

    private final Path mapFile;
    private final Drive driveService;
    private final DirectoryMap mapRoot;
    private final Map<String, DirectoryMap> mappingMap;
    private final Path localRoot;
    private final Logger logger = LoggerFactory.getLogger(FilesystemMapper.class);

    public FilesystemMapper(Path mapFile, Drive driveRemote) throws Exception {
        Objects.requireNonNull(driveRemote, "Drive service may not be null");
        Objects.requireNonNull(mapFile, "Map file may not be null");

        if (!Files.exists(mapFile)) {
            logger.warn("Map file {} does not exist, falling back to default {}", mapFile, DEFAULT_MAP_FILE);
            this.mapFile = DEFAULT_MAP_FILE;
        } else {
            logger.debug("Loading map file {}", mapFile);
            this.mapFile = mapFile;
        }
        this.driveService = driveRemote;
        this.mapRoot = parseMapFile(this.mapFile);
        this.localRoot = this.mapRoot.getLocalPath();
        this.mappingMap = buildMappingMap(this.mapRoot);
    }

    /**
     * Get the local path of a remote folder, identified by its ID.
     *
     * @param remoteId  The remote folder's ID.
     * @return          The matching local path, or {@code null} if there is no mapping for the specified remote ID.
     */
    public Path getLocalPath(String remoteId) {
        DirectoryMap mapping = mappingMap.get(remoteId);
        if (mapping == null) {
            return null;
        }
        return mapping.getLocalPath();
    }

    /**
     * Get the remote ID of the specified local folder, identified by its absolute path.
     * @param localPath Local directory path. If not absolute, is made absolute for searching.
     * @return          The ID of the mapped remote folder, or {@code null} if not mapped.
     */
    public String getRemoteId(Path localPath) {
        if (!localPath.isAbsolute()) {
            localPath = localPath.toAbsolutePath();
        }
        for(DirectoryMap mapping : mappingMap.values()) {
            if (mapping.getLocalPath().equals(localPath)) {
                return mapping.getRemoteId();
            }
        }
        return null;
    }

    /**
     * Parse a map file, containing an JSON object with the structure of the object found in {@link #DEFAULT_MAP_FILE},
     * into a {@link DirectoryMap} with its corresponding subdirectories.
     *
     * @param mapFile  The map file from which to read data.
     * @return          The equivalent Directory.
     */
    private DirectoryMap parseMapFile(Path mapFile) throws Exception {
        Objects.requireNonNull(mapFile);
        JsonObject map = new Gson().fromJson(new FileReader(mapFile.toFile()), JsonObject.class);
        if (!map.has("root") || !map.get("root").isJsonPrimitive()) {
            throw new Exception("Map in " + mapFile + " does not include a valid root directory ID");
        }
        String remoteRootId = map.get("root").getAsString();
        Path localRoot = Paths.get(map.getAsJsonObject(remoteRootId).get("localPath").getAsString()).toAbsolutePath();
        DirectoryMap result = new DirectoryMap(remoteRootId, localRoot);
        parseDirectoryRecursive(result, map);
        return result;
    }

    /**
     * Recursively walks through the specified map, creating a tree of Directories.
     *
     * @param currentRoot   Current directory root.
     * @param map           The map from which to read subdirectories.
     */
    private void parseDirectoryRecursive(DirectoryMap currentRoot, JsonObject map) {
        List<String> entriesToRemove = new ArrayList<>();
        // Find subdirs of current dir
        map.entrySet().stream().filter(entry -> {
            if (!entry.getValue().isJsonObject()) {
                return false;
            }
            for (JsonElement parent : entry.getValue().getAsJsonObject().getAsJsonArray("parents")) {
                if (parent.getAsString().equals(currentRoot.getRemoteId())) {
                    return true;
                }
            }
            return false;
        }).forEach(entry -> {
            // Add them to current directoryMap subdir list
            JsonObject mapSubdir = entry.getValue().getAsJsonObject();
            Path localPath = Paths.get(mapSubdir.getAsJsonPrimitive("localPath").getAsString()).toAbsolutePath();
            DirectoryMap subdir = new DirectoryMap(entry.getKey(), localPath);
            currentRoot.getSubdirs().add(subdir);
            entriesToRemove.add(entry.getKey());
            // Recursively go deeper (DFS)
            parseDirectoryRecursive(subdir, map);
        });
        // Remove all explored subdirs
        entriesToRemove.forEach(map::remove);
    }

    /**
     * Navigate through a directory mapping tree and build a map mapping remote directory IDs to directory mappings for
     * easier access later.
     *
     * @param root  The root mapping
     * @return      The equivalent map.
     */
    private Map<String, DirectoryMap> buildMappingMap(DirectoryMap root) {
        Map<String, DirectoryMap> result = new HashMap<>();
        Deque<DirectoryMap> mappingsToVisit = new LinkedList<>();
        mappingsToVisit.add(root);
        while(!mappingsToVisit.isEmpty()) {
            DirectoryMap current = mappingsToVisit.pop();
            result.put(current.getRemoteId(), current);
            mappingsToVisit.addAll(current.getSubdirs());
        }
        return result;
    }

    public DirectoryMap getMapRoot() {
        return mapRoot;
    }
}
