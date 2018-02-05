package client.download;

import client.Config;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.googleapis.media.MediaHttpDownloaderProgressListener;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.samples.drive.cmdline.FileDownloadProgressListener;
import com.google.api.services.samples.drive.cmdline.FileUploadProgressListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Optional;

public class FileDownloader {
    private final Path outputFile;
    private final Drive drive;
    private final File remoteFile;
    private final MediaHttpDownloaderProgressListener progressListener;
    private final HttpTransport httpTransport;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public FileDownloader(Drive driveClient, File remoteFile, Path outputFile, MediaHttpDownloaderProgressListener downloaderProgressListener) throws GeneralSecurityException, IOException {
        this.drive = driveClient;
        this.remoteFile = remoteFile;
        this.outputFile = outputFile;
        this.progressListener = downloaderProgressListener;

        httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    }

    public FileDownloader(Drive driveClient, File remoteFile, Path outputFile) throws GeneralSecurityException, IOException {
        this(driveClient, remoteFile, outputFile, new FileDownloadProgressListener());
    }

    public void download() throws IOException {
        Path parentDir = outputFile.getParent();
        if (!Files.exists(parentDir)) {
            throw new IllegalStateException("Containing directory " + parentDir +  " does not exist");
        }
        OutputStream out = new FileOutputStream(outputFile.toFile());

        boolean useDirectDownload = Optional.ofNullable(remoteFile.getSize()).orElse(0L) <= Config.MAX_DIRECT_DOWNLOAD_SIZE;
        MediaHttpDownloader downloader = new MediaHttpDownloader(httpTransport, drive.getRequestFactory().getInitializer());
        downloader.setDirectDownloadEnabled(useDirectDownload);
        downloader.setProgressListener(progressListener);
        logger.debug("Downloading remote file {} to {} with {} download", remoteFile.getId(), outputFile, useDirectDownload ? "direct" : "resumable");
        downloader.download(new GenericUrl(remoteFile.getWebViewLink()), out);
        out.close();    // Previous line does not close output stream.
    }
}
