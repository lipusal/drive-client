package client.discovery;

import client.Config;
import client.DirectoryMapping;
import client.FileIgnorer;
import client.FilesystemMapper;
import client.discovery.filtering.FilterStrategy;
import client.discovery.mapping.MappingStrategy;
import client.discovery.sync.SyncStrategy;
import client.discovery.traversal.TraversalStrategy;
import client.download.RemoteExplorer;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Class used to crawl remote folders with different strategies.  Strategies used are:
 * <ul>
 *     <li>{@link TraversalStrategy} to define traversal (eg. {@link client.discovery.traversal.DfsTraversalStrategy DFS}, {@link client.discovery.traversal.BfsTraversalStrategy BFS}</li>
 *     <li>{@link MappingStrategy} to define whether encountered directories should be mapped in the global mapper (eg. {@link client.discovery.mapping.MapIfNotAlreadyMappedStrategy map if not already mapped})</li>
 *     <li>{@link SyncStrategy} to define whether any mapped directories should have their {@code sync} flag set (eg. {@link client.discovery.sync.InheritSyncStrategy inherit from parent})</li>
 *     <li>{@link FilterStrategy} to define whether a directory should be added to the queue of traversed directories (eg. {@link client.discovery.filtering.FilterIfIgnoredStrategy filter ignored}).</li>
 * </ul>
 */
public abstract class AbstractRemoteDiscoverer {
    private final Drive drive;
    protected final FileIgnorer globalIgnorer;
    protected final DirectoryMapping rootMapping;
    protected final RemoteExplorer remoteExplorer;
    protected final FilesystemMapper globalMapper;
    private TraversalStrategy traversalStrategy;
    private MappingStrategy mappingStrategy;
    private SyncStrategy syncStrategy;
    private FilterStrategy filterStrategy;
    protected Consumer<File> consumer;

    /**
     * Instances a discoverer to start on {@code rootMapping}, with the specified strategies.
     */
    public AbstractRemoteDiscoverer(Drive drive, DirectoryMapping rootMapping, TraversalStrategy traversalStrategy, MappingStrategy mappingStrategy, SyncStrategy syncStrategy, FilterStrategy filterStrategy) {
        Objects.requireNonNull(drive);
        Objects.requireNonNull(rootMapping);

        this.drive = drive;
        this.rootMapping = rootMapping;
        this.globalIgnorer = Config.getInstance().getGlobalIgnorer();
        this.globalMapper = Config.getInstance().getGlobalMapper();
        this.traversalStrategy = traversalStrategy;
        this.mappingStrategy = mappingStrategy;
        this.syncStrategy = syncStrategy;
        this.filterStrategy = filterStrategy;
        this.remoteExplorer = new RemoteExplorer(drive);

        if (!globalMapper.isMapped(rootMapping.getRemoteId())) {
            throw new IllegalArgumentException("The specified mapping " + rootMapping + " is not registered. Must supply a registered mapping.");
        }
    }

    /**
     * Convenience method. Instances a discoverer with no strategies defined. Note that all strategies must be defined
     * before calling {@link #discover()} or a {@link NullPointerException} will be thrown.*
     */
    public AbstractRemoteDiscoverer(Drive drive, DirectoryMapping rootMapping) {
        this(drive, rootMapping, null, null, null, null);
    }

    /**
     * (Optional operation) Set a consumer to receive each discovered remote file. Note that, depending on the mapping
     * strategy used, there might not be a mapping for the received {@code File}.
     *
     * @param consumer  Consumer
     */
    public AbstractRemoteDiscoverer setDirectoryConsumer(Consumer<File> consumer) {
        this.consumer = consumer;
        return this;
    }

    public FileIgnorer getGlobalIgnorer() {
        return globalIgnorer;
    }

    public DirectoryMapping getRootMapping() {
        return rootMapping;
    }

    public FilesystemMapper getGlobalMapper() {
        return globalMapper;
    }

    public TraversalStrategy getTraversalStrategy() {
        return traversalStrategy;
    }

    public void setTraversalStrategy(TraversalStrategy traversalStrategy) {
        this.traversalStrategy = traversalStrategy;
    }

    public SyncStrategy getSyncStrategy() {
        return syncStrategy;
    }

    public void setSyncStrategy(SyncStrategy syncStrategy) {
        this.syncStrategy = syncStrategy;
    }

    public FilterStrategy getFilterStrategy() {
        return filterStrategy;
    }

    public void setFilterStrategy(FilterStrategy filterStrategy) {
        this.filterStrategy = filterStrategy;
    }

    public MappingStrategy getMappingStrategy() {
        return mappingStrategy;
    }

    public void setMappingStrategy(MappingStrategy mappingStrategy) {
        this.mappingStrategy = mappingStrategy;
    }

    /**
     * Discover the remote filesystem with the defined strategies.
     * TODO: Return a completable future so this can be done asynchronously?
     */
    public final void discover() throws IOException {
        Objects.requireNonNull(traversalStrategy);
        Objects.requireNonNull(mappingStrategy);
        Objects.requireNonNull(syncStrategy);
        Objects.requireNonNull(filterStrategy);

        while (!traversalStrategy.isDone()) {
            DirectoryMapping currentParent = traversalStrategy.getNextMapping();
            if (currentParent == null) {
                throw new IllegalStateException("Requested next mapping but received null while not done");
            }
            List<File> subdirs = retrieveSubdirs(currentParent);    // TODO: Extract to strategy as well?
            for (File subdir : subdirs) {   // Lambda doesn't work without a try/catch here
                String remoteId = subdir.getId();
                if (mappingStrategy.shouldMap(remoteId)) {
                    // TODO try to build local path with currentParent.getLocalPath().resolve() or something
                    globalMapper.mapSubdir(remoteId, Paths.get(currentParent.getLocalPath().toString(), subdir.getName()), syncStrategy.shouldSync(subdir, currentParent), currentParent);
                }
                if (consumer != null) {
                    consumer.accept(subdir);
                }
                DirectoryMapping subMapping = globalMapper.getMapping(remoteId);
                if (subMapping != null && !filterStrategy.isFiltered(subMapping, currentParent)) {
                    traversalStrategy.addSubmapping(subMapping, currentParent);
                }
            }
        }
    }

    /**
     * Retrieve the appropriate subdirectories of the remote dir corresponding to the specified mapping.
     *
     * @param parent    The mapping of the parent whose directories to retrieve.
     * @return          The remote files.
     */
    protected List<File> retrieveSubdirs(DirectoryMapping parent) throws IOException {
        return remoteExplorer.getSubdirs(parent.getRemoteId());
    }
}
