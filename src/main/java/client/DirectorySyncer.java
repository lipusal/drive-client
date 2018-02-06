package client;

import client.download.FileDownloader;
import client.download.RemoteExplorer;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Class in charge of synchronizing a local directory to a remote directory, and viceversa.
 */
public class DirectorySyncer {
    private final DirectoryMap directoryMap;
    private final Drive driveService;
    private final RemoteExplorer remoteExplorer;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public DirectorySyncer(DirectoryMap directoryMap, Drive driveRemote/*, TODO connection pool to request upload/download*/) {
        Objects.requireNonNull(directoryMap);
        Objects.requireNonNull(driveRemote);

        this.driveService = driveRemote;
        this.directoryMap = directoryMap;
        this.remoteExplorer = new RemoteExplorer(driveService);
    }

    public void sync() throws IOException, GeneralSecurityException {
        logger.debug("Syncing {} to {}", directoryMap.getLocalPath(), directoryMap.getRemoteId());
        List<File> remoteFiles = pull();
        List<File> remoteDirs = remoteFiles.stream().filter(file -> file.getMimeType().equals(Config.FOLDER_MIME_TYPE)).collect(Collectors.toList());
        remoteDirs.add(0, new File().setId(directoryMap.getRemoteId()).setName("."));   // Make sure the containing directory exists first
        remoteFiles.removeAll(remoteDirs);  // Keep only files in remoteFiles

        createLocalDirs(remoteDirs);
        downloadFiles(remoteFiles);
        // TODO diff local with remote and upload new files to remote
    }

    private List<File> pull() throws IOException {
        return remoteExplorer.getContents(directoryMap.getRemoteId());
    }

    private void push() {

    }

    private void diff() {

    }

    /**
     * Creates any directories that don't exist locally out of the provided list of remote directories. Existence of
     * local directories is made relative to {@link #directoryMap}'s {@code localPath}.
     *
     * @param remoteDirs    Remote directories to create locally.
     * @throws IOException  On I/O errors when creating directories.
     */
    private void createLocalDirs(List<File> remoteDirs) throws IOException {
        for(File dir : remoteDirs) {
            Path localPath = Paths.get(directoryMap.getLocalPath().toString(), dir.getName()).normalize();
            if (!Files.exists(localPath)) {
                logger.debug("Creating local directory {}, mapped to remote directory {}", localPath, dir.getId());
                Files.createDirectory(localPath);
            } else if (!Files.isDirectory(localPath)) {
                logger.error("Local file {} already exists, can't create a file of the same name (I think?). Aborting.", localPath);
                throw new IllegalStateException("Can't create local directory " + localPath + ": A file of the same name already exists.");
            }
        }
        // TODO NOW register mapping with FilesystemMapper
    }

    /**
     * Download missing or outdated remote files.
     *
     * @param remoteFiles                   The remote files to download.
     */
    private void downloadFiles(List<File> remoteFiles) throws GeneralSecurityException, IOException {
        for(File remoteFile : remoteFiles) {
            Path localPath = Paths.get(directoryMap.getLocalPath().toString(), remoteFile.getName());
            ZonedDateTime remoteLastModified = ZonedDateTime.parse(remoteFile.getModifiedTime().toStringRfc3339()),
            localLastModified = Files.exists(localPath) ? ZonedDateTime.parse(Files.getLastModifiedTime(localPath).toString()) : null;

            if (!Files.exists(localPath) || remoteLastModified.isAfter(localLastModified)) {
                // TODO: Spread this over various threads (ie. executor service), use various channels per download, etc.
                new FileDownloader(driveService, remoteFile, localPath).download();
            } else {
                logger.debug("{} already up to date", localPath);
            }
        }
    }
}