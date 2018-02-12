package client;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Folders in Drive are exclusively identified with IDs, and every folder's parents are merely another property of the
 * folder. This class constructs a tree-like structure to map remote folder IDs to local folders.
 */
public class FilesystemMapper {
    private static final Path DEFAULT_MAP_FILE = Paths.get("map.default.json").toAbsolutePath();

    private final Path mapFile;
    private final Drive driveService;
    private final DirectoryMapping mapRoot;
    private final Map<String, DirectoryMapping> mappingMap;
    private final Path localRoot;
    private final String remoteRoot;
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
        this.remoteRoot = this.mapRoot.getRemoteId();
        this.mappingMap = buildMappingMap(this.mapRoot);
    }

    public DirectoryMapping getRootMapping() {
        return mapRoot;
    }

    /**
     * Get a directory mapping by its remote ID.
     *
     * @param remoteId  The remote folder's ID.
     * @return          The matching directory mapping, or {@code null} if there is no mapping for the specified remote ID.
     */
    public DirectoryMapping getMapping(String remoteId) {
        return mappingMap.get(remoteId);
    }

    /**
     * Get a directory mapping by its absolute local path.
     * @param localPath Local directory path. If not absolute, is made absolute for searching.
     * @return          The matching directory mapping, or {@code null} if not mapped.
     */
    public DirectoryMapping getMapping(Path localPath) {
        if (!localPath.isAbsolute()) {
            localPath = localPath.toAbsolutePath();
        }
        for(DirectoryMapping mapping : mappingMap.values()) {
            if (mapping.getLocalPath().equals(localPath)) {
                return mapping;
            }
        }
        return null;
    }

    /**
     * Get the local path of a remote folder, identified by its ID.
     *
     * @param remoteId  The remote folder's ID.
     * @return          The matching local path, or {@code null} if there is no mapping for the specified remote ID.
     */
    public Path getLocalPath(String remoteId) {
        return Optional.ofNullable(getMapping(remoteId)).map(DirectoryMapping::getLocalPath).orElse(null);
    }

    /**
     * Get the remote ID of the specified local folder, identified by its absolute path.
     * @param localPath Local directory path. If not absolute, is made absolute for searching.
     * @return          The ID of the mapped remote folder, or {@code null} if not mapped.
     */
    public String getRemoteId(Path localPath) {
        return Optional.ofNullable(getMapping(localPath)).map(DirectoryMapping::getRemoteId).orElse(null);
    }

    public void mapSubdir(Path localDir, File remoteDir, DirectoryMapping parentMapping) {
        // TODO: Allow mapping files too, rather than just directories?
        // Validation
        Objects.requireNonNull(localDir);
        Objects.requireNonNull(remoteDir);
        Objects.requireNonNull(parentMapping);
        if (!mappingMap.containsValue(parentMapping)) {
            throw new IllegalArgumentException("Supplied parent mapping is not registered, must supply a registered parent mapping");
        }

        String remoteId = remoteDir.getId();
        Optional<DirectoryMapping> mapping = parentMapping.getSubdirById(remoteId);
        if (mapping.isPresent()) {
            // Remote unchanged, update local path
            mapping.get().setLocalPath(localDir);
        } else {
            // Remote unmapped, add subdir to parent
            DirectoryMapping newMapping = new DirectoryMapping(remoteId, localDir, false);
            parentMapping.getSubdirs().add(newMapping);
            // Also add to mapping map
            mappingMap.put(remoteId, newMapping);
        }
        logger.debug("Mapped {} <=> {}", localDir, remoteId);
    }

    /**
     * Convenience method. Calls {@code map(localPath, remoteDir, parentMapping)}.
     * @see #mapSubdir(Path, File, DirectoryMapping)
     */
    public void mapSubdir(File remoteDir, Path localPath, DirectoryMapping parentMapping) {
        mapSubdir(localPath, remoteDir, parentMapping);
    }

    /**
     * Parse a map file, containing an JSON object with the structure of the object found in {@link #DEFAULT_MAP_FILE},
     * into a {@link DirectoryMapping} with its corresponding subdirectories.
     *
     * @param mapFile  The map file from which to read data.
     * @return          The equivalent Directory.
     */
    private DirectoryMapping parseMapFile(Path mapFile) throws Exception {
        Objects.requireNonNull(mapFile);
        JsonObject map = new Gson().fromJson(new FileReader(mapFile.toFile()), JsonObject.class);
        if (!map.has("root") || !map.get("root").isJsonPrimitive()) {
            throw new Exception("Map in " + mapFile + " does not include a valid root directory ID");
        }
        String remoteRootId = map.get("root").getAsString();
        Path localRoot = Paths.get(map.getAsJsonObject(remoteRootId).get("localPath").getAsString()).toAbsolutePath();
        DirectoryMapping result = new DirectoryMapping(remoteRootId, localRoot, false);
        parseDirectoryRecursive(result, map);
        return result;
    }

    /**
     * Recursively walks through the specified map, creating a tree of Directories.
     *
     * @param currentRoot   Current directory root.
     * @param map           The map from which to read subdirectories.
     */
    private void parseDirectoryRecursive(DirectoryMapping currentRoot, JsonObject map) {
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
            DirectoryMapping subdir = new DirectoryMapping(entry.getKey(), localPath, false);
            currentRoot.getSubdirs().add(subdir);
            entriesToRemove.add(entry.getKey());
            // Recursively go deeper (DFS)
            parseDirectoryRecursive(subdir, map);
        });
        // Remove all explored subdirs
        entriesToRemove.forEach(map::remove);
    }

    /**
     * Navigate through a directory mapping tree and build a {@link Map} mapping remote directory IDs to directory
     * mappings for easier access later.
     *
     * @param root  The root mapping
     * @return      The equivalent map.
     */
    private Map<String, DirectoryMapping> buildMappingMap(DirectoryMapping root) {
        Map<String, DirectoryMapping> result = new HashMap<>();
        Deque<DirectoryMapping> mappingsToVisit = new LinkedList<>();
        mappingsToVisit.add(root);
        while(!mappingsToVisit.isEmpty()) {
            DirectoryMapping current = mappingsToVisit.pop();
            result.put(current.getRemoteId(), current);
            mappingsToVisit.addAll(current.getSubdirs());
        }
        return result;
    }

    public Path getLocalRoot() {
        return localRoot;
    }

    public String getRemoteRoot() {
        return remoteRoot;
    }

    @Override
    public String toString() {
        return mapRoot.tree();
    }

    /**
     * Write the current mappings to the configured mapping file.
     *
     * @throws IOException  See {@link Gson#toJson(JsonElement, Appendable)}
     */
    public void writeToFile() throws IOException {
        // TODO: Make this private and manage when to persist to file.

        // Build JSON
        JsonObject result = new JsonObject();
        result.add("root", new JsonPrimitive(getRemoteRoot()));
        buildMapJsonRecursive(mapRoot, result);

        Writer w = new FileWriter(mapFile.toFile());
        new Gson().toJson(result, w);
        w.close();
    }

    /**
     * Recursively build a JSON representation of the current mappings. Call this initially with the mapping root.
     *
     * @param mapping   The current mapping. Call this with the mapping root to cover everything.
     * @param output    The JSON object where to add mappings.
     */
    private void buildMapJsonRecursive(DirectoryMapping mapping, JsonObject output) {
        mapping.getSubdirs().forEach(subMapping -> {
            // TODO: Add sync property, and other data, to DirectoryMapping
            output.add(subMapping.getRemoteId(), mapEntry(subMapping.getName(), subMapping.getLocalPath(), true, mapping.getRemoteId()));
            buildMapJsonRecursive(subMapping, output);  // DFS
        });
    }

    /**
     * Dynamically build a JsonObject with the shape of an entry as defined in the JSON declared in {@link FilesystemMapper#DEFAULT_MAP_FILE}.
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

}
