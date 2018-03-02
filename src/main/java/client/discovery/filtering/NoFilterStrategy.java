package client.discovery.filtering;

import client.DirectoryMapping;

public class NoFilterStrategy implements FilterStrategy {
    @Override
    public boolean isFiltered(DirectoryMapping subdir, DirectoryMapping parent) {
        return false;
    }
}
