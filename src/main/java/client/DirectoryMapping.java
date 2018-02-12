package client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Directory mapping, maps a local directory (via its absolute path) to a remote directory (via its ID) and vice-versa.
 * Also supports adding subdirectories, although walking through directory mappings is not their intended use case. See
 * {@link FilesystemMapper#mappingMap}.
 */
public class DirectoryMapping {
    private final String remoteId;
    private final Path localPath;
    private final String name;
    private final List<DirectoryMapping> subdirs = new ArrayList<>();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public DirectoryMapping(String remoteId, Path localPath, DirectoryMapping... subdirs) {
        this.remoteId = remoteId;
        this.localPath = localPath;
        this.name = localPath.getFileName().toString();
        this.subdirs.addAll(Arrays.asList(subdirs));
    }

    public DirectoryMapping(Path localPath, DirectoryMapping... subdirs) {
        this(null, localPath, subdirs);
    }

    public String getRemoteId() {
        return remoteId;
    }

    public Path getLocalPath() {
        return localPath;
    }

    public String getName() {
        return name;
    }

    public List<DirectoryMapping> getSubdirs() {
        return subdirs;
    }

    public List<DirectoryMapping> getSubdirsByName(String name) {
        return subdirs.stream().filter(directoryMapping -> directoryMapping.name.equals(name)).collect(Collectors.toList());
    }

    public Optional<DirectoryMapping> getSubdirById(String id) {
        return subdirs.stream().filter(directoryMapping -> directoryMapping.remoteId.equals(id)).findFirst();
    }

    @Override
    public String toString() {
        return name + " (" + remoteId + ")";
    }

    public String tree() {
        StringBuilder result = new StringBuilder(this.toString()).append("\n");
        subdirs.forEach(subdir -> result.append("\t").append(subdir.tree()));
        return result.toString();
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
