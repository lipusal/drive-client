package client.discovery;

import client.DirectoryMapping;
import client.discovery.filtering.FilterIfIgnoredStrategy;
import client.discovery.filtering.NoFilterStrategy;
import client.discovery.mapping.MapIfNotAlreadyMappedStrategy;
import client.discovery.sync.SyncStrategy;
import client.discovery.traversal.DfsTraversalStrategy;
import com.google.api.services.drive.Drive;

/**
 * Remote discoverer that traverses remote filesystem all the way to leaves in a DFS fashion. Can be optionally
 * configured to ignore ignore rules (ie. will still traverse ignored directories).
 */
public class NaiveRemoteDiscoverer extends AbstractRemoteDiscoverer {

    public NaiveRemoteDiscoverer(Drive drive, DirectoryMapping rootMapping, SyncStrategy syncStrategy, boolean overrideIgnores) {
        super(drive, rootMapping, new DfsTraversalStrategy(rootMapping), new MapIfNotAlreadyMappedStrategy(), syncStrategy, overrideIgnores ? new NoFilterStrategy() : new FilterIfIgnoredStrategy());
    }

    public NaiveRemoteDiscoverer(Drive drive, DirectoryMapping rootMapping, SyncStrategy syncStrategy) {
        this(drive, rootMapping, syncStrategy, false);
    }
}
