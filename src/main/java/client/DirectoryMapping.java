package client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Directory mapping, maps a local directory (via its absolute path) to a remote directory (via its ID) and vice-versa.
 * Also supports adding subdirectories, although walking through directory mappings is not their intended use case. See
 * {@link FilesystemMapper#mappingMap}.
 */
public class DirectoryMapping {
    private String remoteId;
    private Path localPath;
    private final List<DirectoryMapping> subdirs = new ArrayList<>();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private boolean sync;

    /**
     * Whether <strong>ALL</strong> subdirectories (ie. transitive directories as well) have been fetched from
     * remote, and subdirectory info can be considered up to date.
     */
    private boolean subdirsUpToDate = false;

    public DirectoryMapping(String remoteId, Path localPath, boolean sync, DirectoryMapping... subdirs) {
        this.remoteId = remoteId;
        this.localPath = localPath;
        this.sync = sync;
        this.subdirs.addAll(Arrays.asList(subdirs));
    }

    public String getRemoteId() {
        return remoteId;
    }

    public Path getLocalPath() {
        return localPath;
    }

    public String getName() {
        return localPath.getFileName().toString();
    }

    public List<DirectoryMapping> getSubdirs() {
        return subdirs;
    }

    /**
     * @see #subdirsUpToDate
     */
    public boolean areSubdirsUpToDate() {
        return subdirsUpToDate;
    }

    /**
     * @see #subdirsUpToDate
     */
    public void setSubdirsUpToDate(boolean subdirsUpToDate) {
        this.subdirsUpToDate = subdirsUpToDate;
    }

    public boolean isSynced() {
        return sync;
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public void setRemoteId(String remoteId) {
        this.remoteId = remoteId;
    }

    public void setLocalPath(Path localPath) {
        this.localPath = localPath;
    }

    public List<DirectoryMapping> getSubdirsByName(String name) {
        return subdirs.stream().filter(directoryMapping -> directoryMapping.getName().equals(name)).collect(Collectors.toList());
    }

    public Optional<DirectoryMapping> getSubdirById(String id) {
        return subdirs.stream().filter(directoryMapping -> directoryMapping.remoteId.equals(id)).findFirst();
    }

    public String tree() {
        return treeRecursive(0);
    }

    private String treeRecursive(int depth) {
        // TODO: Do this iteratively with a Deque, to prevent instancing many StringBuilders
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            result.append('\t');
        }
        result.append(this.toString()).append("\n");
        subdirs.forEach(subdir -> result.append(subdir.treeRecursive(depth + 1)));
        return result.toString();
    }

    /**
     * Recursively walks down all subdirs in a DFS fashion, passing each walked subdirectory to a specified {@link Consumer}.
     *
     * @param consumer  Consumer to accept each walked subdirectory.
     */
    public void deepWalkSubdirs(Consumer<DirectoryMapping> consumer) {
        // DFS
        subdirs.forEach(subdir -> {
            consumer.accept(subdir);
            subdir.deepWalkSubdirs(consumer);
        });
    }

    /**
     * Equivalent to {@link #deepWalkSubdirs(Consumer)}, but also includes self.
     *
     * @param consumer Consumer to accept each walked directory.
     */
    public void deepWalkSelfAndSubdirs(Consumer<DirectoryMapping> consumer) {
        consumer.accept(this);
        deepWalkSubdirs(consumer);
    }

    @Override
    public String toString() {
        return getName() + " (" + remoteId + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DirectoryMapping that = (DirectoryMapping) o;
        if ((remoteId == null && that.remoteId != null) || (remoteId != null && that.remoteId == null)) {
            // Exactly one has a remote ID. Compare only local paths
            // TODO: This doesn't sound like a good idea in an equals()...conditional comparison?
            logger.warn("Comparing directories {} and {}, one of them doesn't have a remote ID. Comparing local paths only", this, that);
            return Objects.equals(localPath, that.localPath);
        } else {
        return Objects.equals(remoteId, that.remoteId) &&
                Objects.equals(localPath, that.localPath);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(remoteId, localPath);
    }
}
