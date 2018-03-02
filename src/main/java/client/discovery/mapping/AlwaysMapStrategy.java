package client.discovery.mapping;

public class AlwaysMapStrategy implements MappingStrategy {
    @Override
    public boolean shouldMap(String remoteFileId) {
        return true;
    }
}
