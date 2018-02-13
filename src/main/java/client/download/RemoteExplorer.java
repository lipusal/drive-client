package client.download;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class RemoteExplorer {
    private final Drive drive;

    public RemoteExplorer(Drive driveService) {
        this.drive = driveService;
    }

    /**
     * Find folders with the specified name.
     */
    public List<File> findFoldersByName(String name) throws IOException {
        return drive.files().list().setQ("name='" + name + "' and mimeType='application/vnd.google-apps.folder'").execute().getFiles();
    }

    /**
     * Find folders under the specified parent, with the specified name
     */
    public List<File> findFoldersByName(String name, String parentId) throws IOException {
        try {
            return drive.files().list().setQ("name='" + name + "' and '" + parentId + "' in parents and mimeType='application/vnd.google-apps.folder'").execute().getFiles();
        } catch(GoogleJsonResponseException e) {
            if (e.getDetails().getCode() == 404) {
                // Not found
                return Collections.emptyList();
            } else {
                throw e;
            }
        }
    }

    /**
     * Find a file or folder with the specified ID.
     */
    public File findById(String fileOrFolderId) throws IOException {
        return drive.files().get(fileOrFolderId).setFields("*").execute();
    }

    /**
     * Get subdirectories of the specified directory ID.
     */
    public List<File> getSubdirs(String parentDirId) throws IOException {
        return drive.files().list().setQ("'" + parentDirId + "' in parents and mimeType='application/vnd.google-apps.folder'")
                .setFields("files")     // Want complete file metadata
                .execute().getFiles();
    }

    /**
     * Get the contents of the specified directory.
     *
     * @param directoryId ID of the remote directory.
     * @return  The files.
     */
    public List<File> getContents(String directoryId) throws IOException {
        return drive.files().list().setQ("'" + directoryId + "' in parents")
                .setFields("files")     // Want complete file metadata
                .execute().getFiles();
    }

    /**
     * Fetch parents of the specified file.
     *
     * @param remoteId      The remote file ID.
     * @return              The parents, or {@code null} for none.
     * @throws IOException  On network I/O error.
     */
    public Optional<List<String>> getParents(String remoteId) throws IOException {
        return Optional.ofNullable(findById(remoteId).getParents());
    }
}
