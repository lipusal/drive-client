package client;

import client.download.RemoteExplorer;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Folders in Drive are exclusively identified with IDs, and every folder's parents are merely another property of the
 * folder. This class constructs a tree-like structure to map remote folder IDs to local folders.
 */
public class FilesystemMapper {
    private static final Path DEFAULT_MAP_FILE = Paths.get("map.default.json").toAbsolutePath();

    private final Path mapFile;
    private final Drive driveService;
    private final DirectoryMapping rootMapping;
    private final Map<String, DirectoryMapping> mappingMap;
    private final Path localRoot;
    private final String remoteRoot;
    private final RemoteExplorer remoteExplorer;
    private final Logger logger = LoggerFactory.getLogger(FilesystemMapper.class);

    public FilesystemMapper(Path mapFile, Drive driveRemote) throws Exception {
        Objects.requireNonNull(driveRemote, "Drive service may not be null");
        Objects.requireNonNull(mapFile, "Map file may not be null");

        this.driveService = driveRemote;
        this.remoteExplorer = new RemoteExplorer(driveRemote);

        if (!Files.exists(mapFile)) {
            logger.warn("Map file {} does not exist, using data from config", mapFile);
            // Map file, local and remote roots defined in config
            this.mapFile = Config.getInstance().getMapFilePath();
            this.rootMapping = getRootMappingFromConfig();
            this.localRoot = this.rootMapping.getLocalPath();
            this.remoteRoot = this.rootMapping.getRemoteId();
            // Skeletal mappings map
            this.mappingMap = new HashMap<>();
            mappingMap.put(remoteRoot, rootMapping);
            // Load and map the rest of the synced folders
        } else {
            logger.debug("Loading map file {}", mapFile);
            this.mapFile = mapFile;
            this.rootMapping = parseMapFile(this.mapFile);
            this.localRoot = this.rootMapping.getLocalPath();
            this.remoteRoot = this.rootMapping.getRemoteId();
            this.mappingMap = buildMappingMap(this.rootMapping);
        }
    }

    /**
     * Crawl all of the remote directories to build a representation of the remote structure locally.
     */
    public void crawlRemoteDirs() throws IOException {
        // TODO: Use remoteExplorer#deepGetSubdirs
        List<File> remoteDirs = new LinkedList<>();

        String pageToken = null;
        logger.debug("Crawling remote folders...");

        remoteDirs = loadCrawlResults();
        if (remoteDirs == null) {
            do {
                FileList result = driveService.files().list()
                        .setQ("mimeType='application/vnd.google-apps.folder'")
                        .setSpaces("drive")
                        .setFields("nextPageToken,files(id,name,parents)")
                        .setPageToken(pageToken)
                        .setPageSize(Config.MAX_PAGE_SIZE)
                        .execute();
                remoteDirs.addAll(result.getFiles());
                pageToken = result.getNextPageToken();
                logger.debug("Fetched {} folders, continuing with next page", result.getFiles().size());
            } while (pageToken != null);
        } else {
            logger.debug("Loaded crawl results from file");
        }

        logger.debug("Crawl complete, replicating directory structure of {} directories", remoteDirs.size());

//        saveCrawlResult(remoteDirs);

        Deque<DirectoryMapping> parentsToSee = new LinkedList<>();
        parentsToSee.addFirst(getRootMapping());    // Start at root
        while (!remoteDirs.isEmpty()) {
            // Pop next dir
            DirectoryMapping currentParent = parentsToSee.pop();
            // Get its subdirs
            List<File> remoteSubdirs = remoteDirs.stream().filter(file -> file.getParents() != null && file.getParents().contains(currentParent.getRemoteId())).collect(Collectors.toList());
            remoteSubdirs.forEach(subdir -> {
                // Map it
                mapSubdir(Paths.get(currentParent.getLocalPath().toString(), subdir.getName()), subdir.getId(), false, currentParent);
                // Push newly created mapping as parent to process
                parentsToSee.push(getMapping(subdir.getId()));
            });
            // TODO make the following line faster
            remoteSubdirs.forEach(remoteDirs::remove);
            //remoteDirs.removeAll(remoteSubdirs);    // Much slower, see https://coderanch.com/t/203041/java/Big-performance-hog-Collection-removeAll and https://stackoverflow.com/questions/28671903/hashset-removeall-method-is-surprisingly-slow
        }
        writeToFile();
    }

    public DirectoryMapping getRootMapping() {
        return rootMapping;
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
     * Get whether a remote file is mapped.
     *
     * @param remoteId  The remote file ID.
     * @return          Whether there exists a mapping for the specified remote file.
     */
    public boolean isMapped(String remoteId) {
        return mappingMap.containsKey(remoteId);
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
     * Get whether a local file is mapped.
     *
     * @param localPath     The local file path.
     * @return              Whether there exists a mapping for the specified local file.
     */
    public boolean isMapped(Path localPath) {
        return getMapping(localPath) != null;
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

    /**
     * Adds a directory mapping to the registered mappings, under the specified parent mapping.
     *
     * @param subdir New sub-mapping.
     * @param parent {@code subdir}'s parent directory.
     */
    public void mapSubdir(DirectoryMapping subdir, DirectoryMapping parent) {
        // TODO: Allow mapping files too, rather than just directories?
        // Validation
        Objects.requireNonNull(subdir);
        Objects.requireNonNull(parent);
        if (!mappingMap.containsValue(parent)) {
            throw new IllegalArgumentException("Supplied parent mapping is not registered, must supply a registered parent mapping");
        }
        // FIXME: I don't think the following will ever be true, since mappings' `equals` considers local paths, which will not match. Consider searching by remote ID.
        if (mappingMap.containsValue(subdir) && !parent.getSubdirs().contains(subdir)) {
            throw new UnsupportedOperationException("Remapping an existing subdirectory to a different parent is not currently supported. Attempted to remap " + subdir.getLocalPath() + " under " + parent.getLocalPath());
        }
        // Delete existing mapping if present
        Optional<DirectoryMapping> mapping = parent.getSubdirById(subdir.getRemoteId());
        if (mapping.isPresent()) {
            parent.getSubdirs().remove(subdir);
            logger.debug("Removed existing submapping {} <=> {}", subdir.getLocalPath(), subdir.getRemoteId());
        }
        // Add new mapping
        parent.getSubdirs().add(subdir);
        // Also add to mapping map (should replace existing mapping if present)
        mappingMap.put(subdir.getRemoteId(), subdir);
        logger.debug("Mapped {} <=> {}", subdir.getLocalPath(), subdir.getRemoteId());
    }

    /**
     * Convenience method. Calls {@link #mapSubdir(DirectoryMapping, DirectoryMapping)} with the provided data.
     */
    public void mapSubdir(String remoteDir, Path localPath, boolean sync, DirectoryMapping parentMapping) {
        mapSubdir(new DirectoryMapping(remoteDir, localPath, sync), parentMapping);

    }

    /**
     * Convenience method. Calls {@link #mapSubdir(String, Path, boolean, DirectoryMapping)}.
     */
    public void mapSubdir(Path localDir, String remoteId, boolean synced, DirectoryMapping parentMapping) {
        mapSubdir(remoteId, localDir, synced, parentMapping);
    }

    /**
     * Walk down the remote filesystem from the specified remote directory all the way down to leaves, mapping any newly
     * unmapped directories come across the discovery.
     *
     * @param mapping       The mapping to start from
     * @param syncNewDirs   Whether to set new directories as synced or not.
     * @param consumer      (Optional) Consumer that receives every directory mapping (new or old)  come across.
     * @param maxDepth      (Optional) Maximum depth to reach when discovering, relative to {@code mapping}. {@code -1} is infinity.
     * @throws IllegalArgumentException If {@code mapping} is not registered. Call {@link #mapSubdir(DirectoryMapping, DirectoryMapping)} to register.
     * @throws IOException   On I/O errors while discovering the remote filesystem.
     */
    public void discover(DirectoryMapping mapping, boolean syncNewDirs, Consumer<DirectoryMapping> consumer, int maxDepth) throws IOException {
        if (!isMapped(mapping.getRemoteId())) {
            throw new IllegalArgumentException("The specified mapping " + mapping + " is not registered. Must supply a registered mapping.");
        }
        remoteExplorer.deepGetSubdirs(mapping.getRemoteId(), maxDepth, fileFileSimpleEntry -> {
            File remoteSubdir = fileFileSimpleEntry.getValue();
            DirectoryMapping parentMapping = getMapping(fileFileSimpleEntry.getKey().getId()),
                    currentMapping = getMapping(remoteSubdir.getId());
            if (currentMapping == null) {
                // Unmapped directory, map
                currentMapping = new DirectoryMapping(remoteSubdir.getId(), Paths.get(parentMapping.getLocalPath().toString(), remoteSubdir.getName()), syncNewDirs);
                mapSubdir(currentMapping, parentMapping);
            }
            currentMapping.setSubdirsUpToDate(true);   // Since we will eventually go all the way down the tree
            if (consumer != null) {
                consumer.accept(currentMapping);
            }
        });
        mapping.setSubdirsUpToDate(syncNewDirs);
    }

    /**
     * Convenience method. Calls {@link #discover(DirectoryMapping, boolean, Consumer, int)} with {@code mapping.isSynced()}
     * as value for {@code syncNewDirs}. That is, uses the parent's mapping {@code synced} flag for all its subdirectories.
     */
    public void discover(DirectoryMapping mapping, Consumer<DirectoryMapping> consumer) throws IOException {
        discover(mapping, mapping.isSynced(), consumer, -1);
    }

    /**
     * Convenience method. Calls {@link #discover(DirectoryMapping, boolean, Consumer, int)} with a {@code null} consumer.
     */
    public void discover(DirectoryMapping mapping, boolean syncNewDirs) throws IOException {
        discover(mapping, syncNewDirs, null, -1);
    }

    /**
     * Behaves like {@link #discover(DirectoryMapping, boolean, Consumer, int)}, but limits discovery of remote filesystem
     * according to a specified file ignorer.
     *
     * @param mapping       The mapping to start from
     * @param syncNewDirs   Whether to set new directories as synced or not.
     * @param fileIgnorer   File ignorer to limit discovery of remote filesystem. <strong>NOTE:</strong> Ignored
     *                      remote files are set not to sync, regardless of {@code syncNewDirs}.
     * @param maxDepth      (Optional) Maximum depth to reach while discovering. Call with {@code -1} for infinity.
     * @param consumer      (Optional) Consumer that receives every non-ignored directory mapping (new or old)  come across.
     * @throws IllegalArgumentException If {@code mapping} is not registered. Call {@link #mapSubdir(DirectoryMapping, DirectoryMapping)} to register.
     * @throws IOException   On I/O errors while discovering the remote filesystem.
     */
    public void discover(DirectoryMapping mapping, boolean syncNewDirs, FileIgnorer fileIgnorer, int maxDepth, Consumer<DirectoryMapping> consumer) throws IOException {
        Objects.requireNonNull(mapping);
        Objects.requireNonNull(fileIgnorer);
        if (!isMapped(mapping.getRemoteId())) {
            throw new IllegalArgumentException("The specified mapping " + mapping + " is not registered. Must supply a registered mapping.");
        }
        if (fileIgnorer.isIgnored(mapping.getLocalPath())) {
            logger.warn("Specified base mapping {} is ignored, not discovering", mapping);
            return;
        }

        Deque<Util.Tuple<DirectoryMapping, Integer>> pendingFiles = new LinkedList<>(); // Tuples of the form (directory, depth)
        pendingFiles.add(new Util.Tuple<>(mapping, 0));
        while (!pendingFiles.isEmpty()) {
            Util.Tuple<DirectoryMapping, Integer> tuple = pendingFiles.pop();
            DirectoryMapping parent = tuple.getA();
            int depth = tuple.getB();
            boolean upToDate = true;
            // Get subdirs of current directory
            for (File remoteSubdir: remoteExplorer.getSubdirs(parent.getRemoteId())) {
                DirectoryMapping subMapping = getMapping(remoteSubdir.getId());
                if (subMapping == null) {
                    // Missing mapping, add
                    subMapping = new DirectoryMapping(remoteSubdir.getId(), Paths.get(parent.getLocalPath().toString(), remoteSubdir.getName()), syncNewDirs);
                    mapSubdir(subMapping, parent);
                }
                boolean ignored = fileIgnorer.isIgnored(subMapping.getLocalPath());
                if (!ignored) {
                    if (consumer != null) {
                        consumer.accept(subMapping);
                    }
                    if (depth < maxDepth) {
                        pendingFiles.addLast(new Util.Tuple<>(subMapping, depth +1));
                    } else {
                        upToDate = false;
                        // TODO NOW: Set false to all ancestors
                        // TODO NOW: Move this to a different branch
                    }
                } else {
                    subMapping.setSync(false);
                    logger.debug("Not discovering remote subdirs of {} since it's ignored", subMapping);
                    // TODO: Should we set `upToDate` to false here? We are not discovering the full tree, but the
                    // TODO: like we will have seen everything. But if it's ignored, I think it makes sense to omit those
                    // TODO: when considering the subdirs as up to date.
                }
            }
            parent.setSubdirsUpToDate(upToDate);   // Since we will eventually go all the way down the tree
        }
    }

    /**
     * Get synced directories from {@link Config#getSyncedDirIds()}, map them if missing, and set synced to false for
     * every directory not marked for sync.
     */
    private void syncWithConfig() throws IOException {
        logger.debug("Syncing maps with config");
        mappingMap.values().forEach(mapping -> mapping.setSync(false));     // Clear all sync flags
        for (String remoteId : Config.getInstance().getSyncedDirIds()) {
            Optional<String> parentId = getParents(remoteId).map(parents -> parents.get(0));
            if (parentId.isPresent()) {
                DirectoryMapping parent = getMapping(parentId.get());
                mapSubdir(buildLocalPath(remoteId), remoteId, true, parent);
            } else {
                buildParentMappings(remoteId);
            }
        }
        writeToFile();
    }

    /**
     * Parse a map file, containing an JSON object with the structure of the object found in {@link #DEFAULT_MAP_FILE},
     * into a {@link DirectoryMapping} with its corresponding subdirectories.
     *
     * @param mapFile   The map file from which to read data. May <strong>NOT</strong> be the default map file. For that,
     *                  call {@link #getRootMappingFromConfig()}.
     * @return          The equivalent Directory.
     * @throws IllegalArgumentException If {@code mapFile.equals(DEFAULT_MAP_FILE)}.
     */
    private DirectoryMapping parseMapFile(Path mapFile) throws Exception {
        Objects.requireNonNull(mapFile);
        if (mapFile.equals(DEFAULT_MAP_FILE)) {
            throw new IllegalArgumentException("Provided map file is default map file. Call #getRootMappingFromConfig() to parse that file.");
        }
        JsonObject map = new Gson().fromJson(new FileReader(mapFile.toFile()), JsonObject.class);
        // Set remote root
        if (!map.has("root") || !map.get("root").isJsonPrimitive()) {
            throw new Exception("Map in " + mapFile + " does not include a valid root directory ID");
        }
        String remoteRootId = map.get("root").getAsString();
        // Set local root
        Path localRoot = Paths.get(map.getAsJsonObject(remoteRootId).get("localPath").getAsString()).toAbsolutePath();
        DirectoryMapping result = new DirectoryMapping(remoteRootId, localRoot, true);
        // Recursively parse the rest of the map
        parseDirectoryRecursive(result, map);
        return result;
    }

    /**
     * Get only the root mapping as defined from config. Useful when a map has not been built ans we need a skeletal map
     * to begin mapping the folders specified in config.
     *
     * @return  A mapping for the root directory, with no subdirs, as defined in config.
     */
    private DirectoryMapping getRootMappingFromConfig() {
        return new DirectoryMapping(Config.getInstance().getRemoteRoot(), Config.getInstance().getLocalRoot(), true);
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
            DirectoryMapping subdir = new DirectoryMapping(entry.getKey(), localPath, mapSubdir.get("sync").getAsBoolean());
            currentRoot.getSubdirs().add(subdir);
            entriesToRemove.add(entry.getKey());
            // Recursively go deeper (DFS)
            parseDirectoryRecursive(subdir, map);
        });
        // Remove all explored subdirs
//        entriesToRemove.forEach(map::remove);
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
        return rootMapping.tree();
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
        result.add(getRemoteRoot(), mapEntry("root", getLocalRoot(), true));
        buildMapJsonRecursive(rootMapping, result);

        Writer w = new FileWriter(mapFile.toFile());
        new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(result, w);
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
            output.add(subMapping.getRemoteId(), mapEntry(subMapping.getName(), subMapping.getLocalPath(), subMapping.isSynced(), mapping.getRemoteId()));
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

    /**
     * Builds a local path, relative to local root, of the specified remote file. That is, crawls the remote filesystem
     * up to root and returns an equivalent local path, eg. [root]/a/b/c/file.extension
     *
     * @param remoteId  The remote file ID
     * @return          The corresponding local path
     */
    private Path buildLocalPath(String remoteId) throws IOException {
        // TODO: Use mapping to avoid some network requests. While requesting, map discovered directories.
        File remote = remoteExplorer.findById(remoteId);
        List<String> dirs = new ArrayList<>();
        while (!(remote.getParents() == null || remote.getParents().isEmpty() || remote.getParents().contains(getLocalRoot().toString()))) {
            dirs.add(0, remote.getName());
            remote = remoteExplorer.findById(remote.getParents().get(0));
        }
        // Files in root may not have a parent, add it
        if (dirs.isEmpty()) {
            logger.debug("FilesystemMapper#buildLocalPath() has no subdirs, assuming file is in root");
            dirs.add(remote.getName());
        }
        // Build path, always relative to root
        return Paths.get(getLocalRoot().toString(), dirs.toArray(new String[0]));
    }

    /**
     * Walks the path from the specified remote file to root, mapping each directory come across. Assumes that
     * {@code remoteId} has not been mapped.
     *
     * @param remoteId  The remote ID.
     */
    private void buildParentMappings(String remoteId) throws IOException {
        List<String> remoteDirs = new ArrayList<>();
        List<String> localPaths = new ArrayList<>();
        File remote = remoteExplorer.findById(remoteId);
        remoteDirs.add(remoteId);
        localPaths.add(remote.getName());
        // Build local and remote paths side by side, bottom-up (ie. from file to root).
        boolean done = false;
        do {
            Optional<List<String>> parents = getParents(remoteId);
            if (parents.isPresent()) {
                // Mapping found, use data from that mapping
                DirectoryMapping parentMapping = getMapping(parents.get().get(0));
                if (parentMapping.equals(getRootMapping())) {
                    done = true;
                } else {
                    remoteDirs.add(0, parentMapping.getRemoteId());
                    localPaths.add(0, parentMapping.getName());
                    remoteId = parentMapping.getRemoteId();
                }
            } else {
                // No mapping found, fetch parents
                parents = Optional.ofNullable(remote.getParents());
                if (!parents.isPresent() || parents.get().isEmpty() || parents.get().get(0).equals(getRemoteRoot())) {
                    done = true;
                } else {
                    remoteDirs.add(0, remote.getId());
                    localPaths.add(0, remoteExplorer.findById(parents.get().get(0)).getName());
                    remoteId = parents.get().get(0);
                }
            }
        } while (!done);
        // Reached root, now we know entire remote path. Build mappings top-down.
        List<DirectoryMapping> mappings = new ArrayList<>(remoteDirs.size() + 1);
        mappings.add(getRootMapping());
        for (int i = 0; i < remoteDirs.size(); i++) {
            mappings.add(new DirectoryMapping(remoteDirs.get(i), Paths.get(getLocalRoot().toString(), localPaths.subList(0, i+1).toArray(new String[0])), true));
        }
        // Now register mappings bottom-up
        for (int i = mappings.size() - 1; i >= 1; i--) {
            mapSubdir(mappings.get(i).getLocalPath(), mappings.get(i).getRemoteId(), mappings.get(i).isSynced(), mappings.get(i-1));
        }
    }

    /**
     * Get the parents of the specified folder, all identified by their remote ID.
     *
     * @param remoteId      The directory's remote ID.
     * @return              The directory's parents, or {@code null} if not found.
     */
    public Optional<List<String>> getParents(String remoteId) {
        for (DirectoryMapping mapping : mappingMap.values()) {
            if (mapping.getSubdirById(remoteId).isPresent()) {
                return Optional.of(Collections.singletonList(mapping.getRemoteId()));
            }
        }
        // Not found
        return Optional.empty();
    }

    /**
     * Get parents, or fetch from remote if not mapped.
     *
     * @param remoteId      The directory's remote ID.
     * @return              The directory's parents.
     * @throws IOException  If there is an error fetching
     */
    public Optional<List<String>> getOrFetchParents(String remoteId) throws IOException {
        Optional<List<String>> result = getParents(remoteId);
        if (!result.isPresent()) {
            result = remoteExplorer.getParents(remoteId);
        }
        return result;
    }

    /**
     * Debugging function to save a crawling result because remote crawling takes a long time.
     *
     * @param remoteDirs
     * @throws IOException
     */
    private void saveCrawlResult(List<File> remoteDirs) throws IOException {
        try (FileWriter writer = new FileWriter("remotes.json")) {
            JsonObject json = new JsonObject();
            remoteDirs.forEach(dir -> {
                JsonObject dirJson = new JsonObject();

                List<String> parents = Optional.ofNullable(dir.getParents()).orElse(Collections.emptyList());
                JsonArray parentsJson = new JsonArray(parents.size());
                parents.forEach(parentsJson::add);
                dirJson.add("parents", parentsJson);

                dirJson.add("id", new JsonPrimitive(dir.getId()));
                dirJson.add("name", new JsonPrimitive(dir.getName()));

                json.add(dir.getId(), dirJson);
            });
            Gson g = new Gson();
            g.toJson(json, writer);
        }
    }

    /**
     * Debugging method to load results of a previous crawling generated by {@link #saveCrawlResult(List)}.
     *
     * @return
     * @throws IOException
     */
    private List<File> loadCrawlResults() throws IOException {
        try (FileReader reader = new FileReader("remotes.json")) {
            Gson g = new Gson();
            JsonObject json = g.fromJson(reader, JsonObject.class);

            return json.entrySet().stream().map(entry -> {
                File dir = new File();
                dir.setId(entry.getKey());

                JsonObject dirJson = entry.getValue().getAsJsonObject();

                dir.setParents(Util.streamFromIterator(dirJson.getAsJsonArray("parents").iterator()).map(JsonElement::getAsString).collect(Collectors.toList()));
                dir.setId(dirJson.get("id").getAsString());
                dir.setName(dirJson.get("name").getAsString());
                return dir;
            }).collect(Collectors.toList());

        } catch (FileNotFoundException e) {
            return null;
        }
    }
}
