package client.discovery.sync;

import client.DirectoryMapping;
import com.google.api.services.drive.model.File;

/**
 * Sync strategy that uses the {@code sync} flag of a directory's parent.
 */
public class InheritSyncStrategy implements SyncStrategy {
    @Override
    public boolean shouldSync(File remoteSubdir, DirectoryMapping parent) {
        return parent.isSynced();
    }
}
