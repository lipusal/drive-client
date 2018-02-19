package client.download;

import client.Util;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpDownloaderProgressListener;
import com.google.api.client.http.HttpTransport;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.samples.drive.cmdline.FileDownloadProgressListener;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

/**
 * Google docs (ie. Docs, Slides and Sheets) are not downloadable, but the Backup and Sync app supports downloading a
 * JSON representation of them, which it will open in the browser. This class "downloads" the remote Doc in that Backup-
 * and-Sync-compatible format.  Note that files downloaded with this class will have an additional extension added
 * (eg. ".gdoc", which is what Backup and Sync recognizes).
 */
public class GoogleDocDownloader {
    private final Path outputFile;
    private final Drive drive;
    private final File remoteFile;
    private final MediaHttpDownloaderProgressListener progressListener;
    private final HttpTransport httpTransport;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public GoogleDocDownloader(Drive driveClient, File remoteFile, Path outputFile, MediaHttpDownloaderProgressListener downloaderProgressListener) throws GeneralSecurityException, IOException {
        if (!Util.isGoogleDoc(remoteFile)) {
            throw new IllegalArgumentException("The specified file " + remoteFile.getName() + " is not a Google doc");
        }
        if (Files.isDirectory(outputFile)) {
            throw new IllegalArgumentException("The specified output path " + outputFile + " is a directory");
        }

        this.drive = driveClient;
        this.remoteFile = remoteFile;
        this.outputFile = addDocsExtension(remoteFile, outputFile.toAbsolutePath());
        this.progressListener = downloaderProgressListener;

        httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    }

    public GoogleDocDownloader(Drive driveClient, File remoteFile, Path outputFile) throws GeneralSecurityException, IOException {
        this(driveClient, remoteFile, outputFile, new FileDownloadProgressListener());
    }

    public void download() throws IOException {
        // TODO: Consider adding an abstract superclass to avoid repeated code
        Path parentDir = outputFile.getParent();
        if (!Files.exists(parentDir)) {
            throw new IllegalStateException("Containing directory " + parentDir + " does not exist");
        }
        logger.debug("Downloading reference to Google document {} to {}...", remoteFile.getId(), outputFile);
        try (FileWriter out = new FileWriter(outputFile.toFile())) {
            JsonObject result = new JsonObject();
            result.add("url", new JsonPrimitive(remoteFile.getWebViewLink()));
            result.add("doc_id", new JsonPrimitive(remoteFile.getId()));
            // TODO: Add email under which to view the document
            result.add("resource_id", new JsonPrimitive("document:" + remoteFile.getId()));
            new Gson().toJson(result, out);
        }
        logger.debug("Download of {} ({}) complete", outputFile, remoteFile.getId());
    }

    /**
     * Add the appropriate Docs extension, recognized by Backup and Sync, that corresponds to this downloader's remote
     * file.
     *
     * @param originalPath The original path to add an extension to.
     * @return The {@code originalPath} with the corresponding extension added.
     */
    public static Path addDocsExtension(File remoteFile, Path originalPath) {
        String docsExtension;
        switch (remoteFile.getMimeType()) {
            case Util.GOOGLE_DOC_MIME_TYPE:
                docsExtension = ".gdoc";
                break;
            case Util.GOOGLE_SLIDES_MIME_TYPE:
                docsExtension = ".gslides";
                break;
            case Util.GOOGLE_SHEETS_MIME_TYPE:
                docsExtension = ".gsheet";
                break;
            default:
                throw new IllegalStateException("Unrecognized Google docs type: " + remoteFile.getMimeType());
        }
        return originalPath.resolveSibling(originalPath.getFileName() + docsExtension); // This blew my mind for a sec; we can add a Path and a String?! No, the Path gets converted `toString()`, which works.
    }
}
