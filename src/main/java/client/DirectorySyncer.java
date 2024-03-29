package client;

import client.download.FileDownloader;
import client.download.GoogleDocDownloader;
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
 * Class in charge of synchronizing a local directory to a remote directory, and vice-versa.
 */
public class DirectorySyncer {
    private final DirectoryMapping directoryMapping;
    private final Drive driveService;
    private final RemoteExplorer remoteExplorer;
    private final FilesystemMapper mapper;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public DirectorySyncer(DirectoryMapping directoryMapping, Drive driveRemote, FilesystemMapper filesystemMapper/*, TODO connection pool to request upload/download*/) {
        Objects.requireNonNull(directoryMapping);
        Objects.requireNonNull(driveRemote);

        this.driveService = driveRemote;
        this.directoryMapping = directoryMapping;
        this.remoteExplorer = new RemoteExplorer(driveService);
        this.mapper = filesystemMapper;
    }

    public void sync() throws IOException, GeneralSecurityException {
        logger.debug("Syncing {} to {}", directoryMapping.getLocalPath(), directoryMapping.getRemoteId());
        List<File> remoteFiles = pull();
        List<File> remoteDirs = remoteFiles.stream().filter(Util::isDir).collect(Collectors.toList());
        remoteDirs.add(0, new File().setId(directoryMapping.getRemoteId()).setName("."));   // Make sure the containing directory exists first
        remoteFiles.removeAll(remoteDirs);  // Keep only files in remoteFiles

        createLocalDirs(remoteDirs);
        downloadFiles(remoteFiles);
        // TODO diff local with remote and upload new files to remote
    }

    private List<File> pull() throws IOException {
        return remoteExplorer.getContents(directoryMapping.getRemoteId());
    }

    private void push() {

    }

    private void diff() {

    }

    /**
     * Creates any synced directories that don't exist locally out of the provided list of remote directories. Existence
     * of local directories is made relative to {@link #directoryMapping}'s {@code localPath}.
     *
     * @param remoteDirs    Remote directories to create locally. Any pre-mapped directories that are set not to sync
     *                      will NOT be created. Nonexistent mappings will be created and assumed to sync.
     * @throws IOException  On I/O errors when creating directories.
     */
    private void createLocalDirs(List<File> remoteDirs) throws IOException {
        boolean newMappingsCreated = false;
        for(File dir : remoteDirs) {
            // Try to fetch local path from a pre-existing mapping first
            DirectoryMapping mapping = mapper.getMapping(dir.getId());
            if (mapping != null && !mapping.isSynced()) {
                logger.debug("Not creating un-synced directory {}", mapping);
                continue;
            }
            Path localPath;
            if (mapping != null && mapping.getLocalPath() != null) {
                localPath = mapping.getLocalPath();
            } else {
                // Not mapped, build local path and add to mappings
                localPath = Paths.get(directoryMapping.getLocalPath().toString(), dir.getName()).normalize();
                mapper.mapSubdir(localPath, dir.getId(), true, directoryMapping);
                newMappingsCreated = true;
            }

            if (!Files.exists(localPath)) {
                logger.debug("Creating local directory {}, mapped to remote directory {}", localPath, dir.getId());
                Files.createDirectories(localPath);

            } else if (!Files.isDirectory(localPath)) {
                logger.error("Local file {} already exists, can't create a file of the same name (I think?). Aborting.", localPath);
                throw new IllegalStateException("Can't create local directory " + localPath + ": A file of the same name already exists.");
            }
        }
        if (newMappingsCreated) {
            mapper.writeToFile();
        }
    }

    /**
     * Download missing or outdated remote files.
     *
     * @param remoteFiles   The remote files to download.
     */
    private void downloadFiles(List<File> remoteFiles) throws GeneralSecurityException, IOException {
        for(File remoteFile : remoteFiles) {
            Path localPath = Paths.get(directoryMapping.getLocalPath().toString(), remoteFile.getName());
            if (Util.isGoogleDoc(remoteFile)) {
                localPath = GoogleDocDownloader.addDocsExtension(remoteFile, localPath);
            }
            ZonedDateTime remoteLastModified = ZonedDateTime.parse(remoteFile.getModifiedTime().toStringRfc3339()),
            localLastModified = Files.exists(localPath) ? ZonedDateTime.parse(Files.getLastModifiedTime(localPath).toString()) : null;

            //noinspection ConstantConditions, `localLastModified == null` <=> `!Files.exists(localPath)` and || is short-circuiting, will prevent calling `isAfter(null)`
            if (!Files.exists(localPath) || remoteLastModified.isAfter(localLastModified)) {
                // TODO: Spread this over various threads (ie. executor service), use various channels per download, etc.
                if (Util.isGoogleDoc(remoteFile)) {
                    new GoogleDocDownloader(driveService, remoteFile, localPath).download();
                } else {
                    new FileDownloader(driveService, remoteFile, localPath).download();
                }
            } else {
                logger.debug("{} already up to date", localPath);
            }
        }
    }
}
