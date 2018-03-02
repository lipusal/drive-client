package client.discovery.sync;

import client.DirectoryMapping;
import com.google.api.services.drive.model.File;

public class AlwaysSyncStrategy implements SyncStrategy {
    @Override
    public boolean shouldSync(File remoteSubdir, DirectoryMapping parent) {
        return true;
    }
}
