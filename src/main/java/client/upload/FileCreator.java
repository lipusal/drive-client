package client.upload;

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.samples.drive.cmdline.FileUploadProgressListener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.Callable;

public class FileCreator implements Callable<File> {

    private final Path file;
    private final Drive drive;
    private final MediaHttpUploaderProgressListener progressListener;

    @SuppressWarnings("SpellCheckingInspection")
    private static final String ROOT_DRIVE_FOLDER_ID = "13N1obnCJg-Bt3M5SOIKE8PSJvoHaCjNl";

    public FileCreator(Drive driveClient, Path fileToUpload, MediaHttpUploaderProgressListener uploaderProgressListener) {
        this.drive = driveClient;
        this.file = fileToUpload;
        this.progressListener = uploaderProgressListener;
    }

    public FileCreator(Drive driveClient, Path fileToUpload) {
        this(driveClient, fileToUpload, new FileUploadProgressListener());
    }

    @Override
    public com.google.api.services.drive.model.File call() throws Exception {
        File fileMetadata = new File();
        fileMetadata.setName(file.getFileName().toString());
        fileMetadata.setParents(Collections.singletonList(ROOT_DRIVE_FOLDER_ID));   // Upload to client root folder for now

        // TODO see upload files in corresponding Drive folder; see https://developers.google.com/drive/v3/web/folder
        FileContent mediaContent = new FileContent(Files.probeContentType(file), file.toFile());
        Drive.Files.Create insert = drive.files().create(fileMetadata, mediaContent).setFields("id");   // Return file ID when creating
        MediaHttpUploader uploader = insert.getMediaHttpUploader();
        uploader.setDirectUploadEnabled(Files.size(file) <= 5e+6);  // Use resumable upload for files > 5MB
        uploader.setProgressListener(progressListener);
        return insert.execute();
    }
}
