package client.discovery.filtering;

import client.DirectoryMapping;

/**
 * While all subdirectories of a given directory may be fetched and/or mapped during discovery, not all subdirectories
 * may necessarily need to be further traversed.  This strategy filters out subdirectories to limit discovery.
 */
@FunctionalInterface
public interface FilterStrategy {

    /**
     * Whether the specified subdirectory is filtered (and thus NOT added for further discovery).
     *
     * @param subdir  Mapping of subdirectory.
     * @param parent  Mapping of subdirectory's parent.
     * @return        Whether the subdir is filtered.
     */
    boolean isFiltered(DirectoryMapping subdir, DirectoryMapping parent);
}
