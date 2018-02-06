package client.download;

import client.Config;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.googleapis.media.MediaHttpDownloaderProgressListener;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.samples.drive.cmdline.FileDownloadProgressListener;
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
        logger.debug("Downloading remote file {} to {}...", remoteFile.getId(), outputFile);
        try (OutputStream out = new FileOutputStream(outputFile.toFile())) {
            drive.files().get(remoteFile.getId()).executeMediaAndDownloadTo(out);
        }
        logger.debug("Download of {} ({}) complete", outputFile, remoteFile.getId());
    }

    /**
     * Use download managers to use direct/resumable uploads, etc. Currently unused due to exceptions thrown on download.
     */
    private void downloadAdvanced() throws IOException {
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
        GenericUrl url = drive.files().get(remoteFile.getId()).buildHttpRequestUrl();
        try {
            downloader.download(url, out);
        } catch(NullPointerException e) {
            // FIXME: Investigate how to fix this (maybe we're building URLs incorrectly, so we don't get the headers we want?)
            StackTraceElement firstStackTraceElement = e.getStackTrace()[0];
            if (firstStackTraceElement.getClassName().equals(MediaHttpDownloader.class.getName())
                    && firstStackTraceElement.getMethodName().equals("download")
                    && firstStackTraceElement.getLineNumber() == 186) {
                /**
                 * This exception originates from {@link MediaHttpDownloader#download(GenericUrl, OutputStream)} line 186
                 * when the server doesn't send a `Content-Length` header back. This happens after everything was downloaded
                 * though, so we can safely close the stream knowing that we didn't miss anything. However, any download
                 * listeners don't get notified of completion, because the flow is interrupted with this exception.
                 * */
                logger.warn("Caught NPE due to missing `Content-Length` header. Ignoring.", e);
            } else {
                throw e;
            }
        }
        out.close();    // Previous line does not close output stream.
    }
}
