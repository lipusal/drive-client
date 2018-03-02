package client.discovery.mapping;

/**
 * Strategy to determine whether a specified remote directory should be mapped.
 */
@FunctionalInterface
public interface MappingStrategy {

    /**
     * Whether a remote file should be mapped.
     *
     * @param remoteFileId  ID of the remote file.
     * @return              Whether to map the remote file.
     */
    boolean shouldMap(String remoteFileId);
}
