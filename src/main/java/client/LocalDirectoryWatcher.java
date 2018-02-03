package client;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public class LocalDirectoryWatcher implements Runnable {
    private static final FileChangeListener defaultFileCreatedListener = changedFile -> System.out.format("%s created\n", changedFile.getFileName());
    private static final FileChangeListener defaultFileModifiedListener = changedFile -> System.out.format("%s changed\n", changedFile.getFileName());
    private static final FileChangeListener defaultFileDeletedistener = changedFile -> System.out.format("%s deleted\n", changedFile.getFileName());

    private final WatchService watcher;
    private FileChangeListener createdListener = defaultFileCreatedListener, modifiedListener = defaultFileModifiedListener, deletedListener = defaultFileDeletedistener;

    public LocalDirectoryWatcher(Path directoryRoot, boolean recursive) throws IOException {
        if (!Files.isDirectory(directoryRoot)) {
            throw new IllegalArgumentException("Specified root is a file, not a directory");
        }
        if (recursive) {
            // TODO
            throw new UnsupportedOperationException("Not implemented yet, sorry");
        }
        this.watcher = directoryRoot.getFileSystem().newWatchService();
        // Watch for add, delete and modify
        directoryRoot.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
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
                watchKey = watcher.take();                      //This is blocking, also see #poll() and #poll(long, TimeUnit)
            } catch (InterruptedException e) {
                System.out.println("Interrupted, will stop watching.");
                watching = false;
                continue;
            }
            List<WatchEvent<?>> events = watchKey.pollEvents(); // Blocking
            for (WatchEvent event : events) {
                Path target = ((Path) event.context());
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
