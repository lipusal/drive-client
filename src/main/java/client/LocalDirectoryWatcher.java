package client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class LocalDirectoryWatcher implements Runnable {
    private static final FileChangeListener defaultFileCreatedListener = changedFile -> System.out.format("%s created\n", changedFile.getFileName());
    private static final FileChangeListener defaultFileModifiedListener = changedFile -> System.out.format("%s changed\n", changedFile.getFileName());
    private static final FileChangeListener defaultFileDeletedListener = changedFile -> System.out.format("%s deleted\n", changedFile.getFileName());

    private final Path ROOT;
    private final WatchService watcher;
    private FileChangeListener createdListener = defaultFileCreatedListener, modifiedListener = defaultFileModifiedListener, deletedListener = defaultFileDeletedListener;
    private final Logger logger = LoggerFactory.getLogger(LocalDirectoryWatcher.class);

    public LocalDirectoryWatcher(Path directoryRoot, boolean recursive) throws IOException {
        if (!Files.isDirectory(directoryRoot)) {
            throw new IllegalArgumentException("Specified root is a file, not a directory");
        }
        if (recursive) {
            // TODO
            throw new UnsupportedOperationException("Not implemented yet, sorry");
        }
        this.ROOT = directoryRoot;
        this.watcher = ROOT.getFileSystem().newWatchService();
        // Watch for add, delete and modify
        ROOT.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
    }

    public LocalDirectoryWatcher(Path directoryRoot) throws IOException {
        this(directoryRoot, false);
    }

    public void setCreatedListener(FileChangeListener createdListener) {
        this.createdListener = createdListener;
    }

    public void setModifiedListener(FileChangeListener modifiedListener) {
        this.modifiedListener = modifiedListener;
    }

    public void setDeletedListener(FileChangeListener deletedListener) {
        this.deletedListener = deletedListener;
    }

    @Override
    public void run() {
        boolean watching = true;
        while (watching) {
            WatchKey watchKey;
            try {
                watchKey = watcher.poll(1, TimeUnit.SECONDS);                      //This is blocking, also see #poll() and #poll(long, TimeUnit)
                if (watchKey == null) {
                    // Tick; want to do anything here?
                    continue;
                }
                if (!watchKey.isValid()) {
                    logger.debug("Invalid watch key, skipping");
                    continue;
                }
            } catch (InterruptedException e) {
                logger.debug("Interrupted, will stop watching.");
                watching = false;
                continue;
            }
            for (WatchEvent event : watchKey.pollEvents()) {
                Path target = Paths.get(ROOT.toString(), event.context().toString()).toAbsolutePath();
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                    createdListener.onChange(target);
                } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                    deletedListener.onChange(target);
                } else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                    modifiedListener.onChange(target);
                } else {
                    System.err.println("Unknown event kind detected: " + event.kind().name());
                }
            }
            // Done, mark the watch key as ready to receive more events. If we don't t do this, we won't receive further events.
            watchKey.reset();
        }
    }
}
