package client.discovery.sync;

import client.Config;
import client.DirectoryMapping;
import com.google.api.services.drive.model.File;

import java.nio.file.Paths;
import java.util.Optional;

/**
 * Set directories as synced if their would-be local path is not ignored.
 */
public class SyncIfNotIgnoredStrategy implements SyncStrategy {
    @Override
    public boolean shouldSync(File remoteSubdir, DirectoryMapping parent) {
        String dirName = Optional.ofNullable(remoteSubdir.getName()).orElseThrow(() -> new IllegalArgumentException("Received a File with no name, can't check if ignored"));
        // TODO: Use parent mapping's ignore
        return !Config.getInstance().getGlobalIgnorer().isIgnored(Paths.get(parent.getLocalPath().toString(), dirName));
    }
}
