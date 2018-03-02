package client.discovery.filtering;

import client.Config;
import client.DirectoryMapping;

/**
 * Filters directories if they are globally ignored. TODO: Also filter if ignored by a specific mapping's ignore rules.
 */
public class FilterIfIgnoredStrategy implements FilterStrategy {
    @Override
    public boolean isFiltered(DirectoryMapping subdir, DirectoryMapping parent) {
        return Config.getInstance().getGlobalIgnorer().isIgnored(subdir.getLocalPath());
        // TODO: Use ignorer from mapping
    }
}
