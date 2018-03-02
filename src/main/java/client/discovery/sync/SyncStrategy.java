package client.discovery.sync;

import client.DirectoryMapping;
import com.google.api.services.drive.model.File;

/**
 * Strategy to define whether a new subdirectory should be set to sync or not upon mapping.
 */
@FunctionalInterface
public interface SyncStrategy {

    /**
     * Whether a newly discovered remote directory should be set to sync.
     *
     * @param remoteSubdir  Remote subdir.
     * @param parent        Mapping of parent directory. May be {@code null} in case {@code remoteSubdir} is remote root.
     * @return  Whether the mapping for the remote directory should be set to sync or not.
     */
    boolean shouldSync(File remoteSubdir, DirectoryMapping parent);
}
