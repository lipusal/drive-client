package client.upload;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import java.nio.file.Path;
import java.util.concurrent.Callable;

public class DirectoryCreator implements Callable<File> {

    private final Path dir;
    private final Drive drive;

    public DirectoryCreator(Drive driveClient, Path directoryToCreate) {
        this.drive = driveClient;
        this.dir = directoryToCreate;
    }

    @Override
    public File call() throws Exception {
        // https://developers.google.com/drive/v3/web/folder
        File fileMetadata = new File();
        fileMetadata.setName(dir.getFileName().toString());
        fileMetadata.setMimeType("application/vnd.google-apps.folder");

        return drive.files().create(fileMetadata).setFields("id").execute();
    }
}
