package client.discovery.mapping;

import client.Config;

/**
 * Maps a file if not already mapped.
 */
public class MapIfNotAlreadyMappedStrategy implements MappingStrategy {
    @Override
    public boolean shouldMap(String remoteFileId) {
        return !Config.getInstance().getGlobalMapper().isMapped(remoteFileId);
    }
}
