package client.discovery;

import client.DirectoryMapping;
import client.discovery.filtering.FilterIfIgnoredStrategy;
import client.discovery.filtering.FilterStrategy;
import client.discovery.mapping.MapIfNotAlreadyMappedStrategy;
import client.discovery.mapping.MappingStrategy;
import client.discovery.sync.SyncStrategy;
import client.discovery.traversal.DfsTraversalStrategy;
import com.google.api.services.drive.Drive;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Remote discoverer that traverses remote filesystem up to a specified depth from the root mapping.
 */
public class DepthLimitedRemoteDiscoverer extends AbstractRemoteDiscoverer {
    private final int maxDepth;
    private final Map<DirectoryMapping, Integer> depths = new HashMap<>();
    private final FilterStrategy secondFilterStrategy;

    public DepthLimitedRemoteDiscoverer(Drive drive, DirectoryMapping rootMapping, MappingStrategy mappingStrategy, SyncStrategy syncStrategy, FilterStrategy secondFilterStrategy, int maxDepth) {
        super(drive, rootMapping, null, mappingStrategy, syncStrategy, null);
        Objects.requireNonNull(secondFilterStrategy);

        this.maxDepth = maxDepth;
        this.secondFilterStrategy = secondFilterStrategy;
        setTraversalStrategy(new DepthTrackingDfsTraversalStrategy(rootMapping));
        setFilterStrategy(new DepthFilterStrategy());
    }

    /**
     * Convenience method. Calls {@link #DepthLimitedRemoteDiscoverer(Drive, DirectoryMapping, MappingStrategy, SyncStrategy, FilterStrategy, int)}
     * with a {@link MapIfNotAlreadyMappedStrategy}.
     */
    public DepthLimitedRemoteDiscoverer(Drive drive, DirectoryMapping rootMapping, SyncStrategy syncStrategy, FilterStrategy filterStrategy, int maxDepth) {
        this(drive, rootMapping, new MapIfNotAlreadyMappedStrategy(), syncStrategy, filterStrategy, maxDepth);
    }

    /**
     * Convenience method. Calls {@link #DepthLimitedRemoteDiscoverer(Drive, DirectoryMapping, MappingStrategy, SyncStrategy, FilterStrategy, int)}
     * with a {@link MapIfNotAlreadyMappedStrategy} and a {@link FilterIfIgnoredStrategy}.
     */
    public DepthLimitedRemoteDiscoverer(Drive drive, DirectoryMapping rootMapping, SyncStrategy syncStrategy, int maxDepth) {
        this(drive, rootMapping, new MapIfNotAlreadyMappedStrategy(), syncStrategy, new FilterIfIgnoredStrategy(), maxDepth);
    }

    /**
     * DFS traversal strategy that keeps track of the depth of each subdirectory, relative to {@link #rootMapping}.
     */
    private class DepthTrackingDfsTraversalStrategy extends DfsTraversalStrategy {

        DepthTrackingDfsTraversalStrategy(DirectoryMapping rootMapping) {
            super(rootMapping);
            depths.put(rootMapping, 0);
        }

        /**
         * Get the next mapping and remove its depth tracking.
         */
        @Override
        public DirectoryMapping getNextMapping() throws IllegalStateException {
            DirectoryMapping result = super.getNextMapping();
            return result;
        }

        /**
         * Add a mapping and track its depth.
         *
         * @param mapping The mapping to add.
         * @param parent  The mapping's parent.
         * @throws IllegalStateException If not done.
         * @throws IllegalArgumentException If {@code parent} has not been added before.
         */
        @Override
        public void addSubmapping(DirectoryMapping mapping, DirectoryMapping parent) throws IllegalStateException {
            super.addSubmapping(mapping, parent);

            int parentDepth = Optional.ofNullable(depths.get(parent)).orElseThrow(() -> new IllegalArgumentException(parent + " has not been added before"));
            depths.put(mapping, parentDepth + 1);
        }
    }

    /**
     * Filter strategy that first filters directories based on depth, and then by {@link #secondFilterStrategy}.
     */
    private class DepthFilterStrategy implements FilterStrategy {

        @Override
        public boolean isFiltered(DirectoryMapping subdir, DirectoryMapping parent) {
            int depth = Optional.ofNullable(depths.get(parent)).map(parentDepth -> parentDepth + 1).orElseThrow(() -> new IllegalArgumentException(parent + " has no registered depth mapping"));
            return depth > maxDepth || secondFilterStrategy.isFiltered(subdir, parent);
        }
    }
}
