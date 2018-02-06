package client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class DirectoryMap {
    private final String remoteId;
    private final Path localPath;
    private final String name;
    private final List<DirectoryMap> subdirs = new ArrayList<>();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public DirectoryMap(String remoteId, Path localPath, DirectoryMap... subdirs) {
        this.remoteId = remoteId;
        this.localPath = localPath;
        this.name = localPath.getFileName().toString();
        this.subdirs.addAll(Arrays.asList(subdirs));
    }

    public DirectoryMap(Path localPath, DirectoryMap... subdirs) {
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

    public List<DirectoryMap> getSubdirs() {
        return subdirs;
    }

    public List<DirectoryMap> getSubdirsByName(String name) {
        return subdirs.stream().filter(directoryMap -> directoryMap.name.equals(name)).collect(Collectors.toList());
    }

    public Optional<DirectoryMap> getSubdirById(String id) {
        return subdirs.stream().filter(directoryMap -> directoryMap.remoteId.equals(id)).findFirst();
    }

    // TODO: Create a Map<String(directoryId), DirectoryMap> elsewhere to have O(1) access to whole tree.
    public Optional<DirectoryMap> getSubdirByIdRecursive(String id) {
        Optional<DirectoryMap> result = getSubdirById(id);
        if (result.isPresent()) {
            return result;
        } else {
            for(DirectoryMap subdir : getSubdirs()) {
                result = subdir.getSubdirByIdRecursive(id);
                if (result.isPresent()) {
                    return result;
                }
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return name + " (" + remoteId + ")";
    }

    public String tree() {
        StringBuilder result = new StringBuilder(this.toString()).append("\n");
        subdirs.forEach(subdir -> {
            result.append("\t").append(subdir.tree());
        });
        return result.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DirectoryMap that = (DirectoryMap) o;
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
