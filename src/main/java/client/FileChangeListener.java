package client;

import java.nio.file.Path;

@FunctionalInterface
public interface FileChangeListener {
    void onChange(Path changedFile);
}
