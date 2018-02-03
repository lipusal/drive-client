package client;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Folders in Drive are exclusively identified with IDs, and every folder's parents are merely another property of the
 * folder. This class constructs a tree-like structure to map remote folder IDs to local folders, in order to be tell
 * whether a folder should be ignored or not.
 */
public class FilesystemMapper {
    private final Path ROOT;
    private final Path outputFile;
    private final Drive drive;

    public FilesystemMapper(Path localRoot, Drive driveRemote, Path dictionaryOutputFile) {
        this.ROOT = localRoot;
        this.drive = driveRemote;
        this.outputFile = dictionaryOutputFile;
    }

    public void crawlRemote() throws IOException {
        // Start with an empty tree, loaded from the specified JSON file
//        JsonObject tree = new Gson().fromJson(
//            new JsonReader(new InputStreamReader(
//                getClass().getResourceAsStream("base_tree.json")
//            )),
//            Directory.class
//        );
        Directory root = new Directory("root", "<ROOT>");
        for(File subdir : getSubfolders("root")) {
            root.getSubdirs().add(new Directory(subdir.getId(), subdir.getName()));
        }
        new Gson().toJson(root, Directory.class, new JsonWriter(new FileWriter(outputFile.toFile())));
//        ExecutorService executorService = Executors.newFixedThreadPool(5);
    }

    private List<File> getSubfolders(String folderId) throws IOException {
        boolean firstPage = true;
        String nextPageToken = null;
        List<File> result = new ArrayList<>();
        while (firstPage || nextPageToken != null) {
            Drive.Files.List request = drive.files().list()
//                    .setQ("mimeType='application/vnd.google-apps.folder' and '" + folderId + "' in parents")
//                    .setQ("mimeType='application/vnd.google-apps.folder'")
//                    .setQ("mimeType='text/plain'")
//                    .setQ("'" + folderId + "' in parents")
                    .setQ("mimeType contains 'image/'")
                    .setFields("*");
            ;
            if (!firstPage) {
                request.setPageToken(nextPageToken);
            } else {
                firstPage = false;
            }
            FileList response = request.execute();
            if (response.getIncompleteSearch()) {
                System.err.println("WARNING: Searching subfolders of folder with ID" + folderId + " yielded an incomplete search.");
            }
            result.addAll(response.getFiles());
            nextPageToken = response.getNextPageToken();
        }
        return result;
    }
}
