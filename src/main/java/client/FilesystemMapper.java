package client;

import client.download.RemoteExplorer;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Folders in Drive are exclusively identified with IDs, and every folder's parents are merely another property of the
 * folder. This class constructs a tree-like structure to map remote folder IDs to local folders.
 */
public class FilesystemMapper {
    private final Path localRoot;
    private final Directory mapRoot;
    private final Path outputFile;
    private final Drive drive;
    private final Logger logger = LoggerFactory.getLogger(FilesystemMapper.class);

    public FilesystemMapper(Path localRoot, Drive driveRemote, Path dictionaryOutputFile) {
        this.localRoot = localRoot;
        this.mapRoot = new Directory("root", "ROOT");
        this.drive = driveRemote;
        this.outputFile = dictionaryOutputFile;
    }

    public Path mapToLocal(Path remotePath) {
        // Walk through map, fetching any missing paths in the middle
        Directory remoteDir = mapRoot;
        List<String> localDirs = new ArrayList<>(); // Does NOT include root name intentionally
        for(Path subdirId : remotePath) {
            Directory nextDir = remoteDir.getSubdirById(subdirId.getFileName().toString()).orElseGet(() -> {
                try {
                    // TODO take advantage of this remote request, register it in the map (though it would be weird to have all folder IDs without having them mapped already...)
                    return new Directory(new RemoteExplorer(drive).findById(subdirId.getFileName().toString()));
                } catch (IOException e) {
                    logger.error("Couldn't map remote path '{}' to local, specifically in the '{}' part: {}", remotePath, subdirId, e.getStackTrace());
                    System.exit(1);
                }
                return null;
            });
            localDirs.add(remoteDir.getName());
        }
        return Paths.get(mapRoot.getName(), localDirs.toArray(new String[0]));
    }

    /**
     * Map a path of directory names (can be local or remote) to remote directory IDs.
     *
     * @param namedPath         Named path, eg. "a/b/c"
     * @return                  The corresponding remote path, eg. "id-1/id-2/id-3"
     * @throws NoSuchElementException   When the specified path does not exist in the remote
     * @throws IOException              See {@link com.google.api.client.googleapis.services.AbstractGoogleClientRequest#execute()}
     */
    public Path mapToIds(Path namedPath) throws IOException {
        Directory currentRemoteDir = mapRoot;
        List<String> pathSections = new ArrayList<>();
        for(Path localSubdir : namedPath) {
            String localSubdirName = localSubdir.getFileName().toString();
            File remoteDir = new RemoteExplorer(drive).findFoldersByName(localSubdirName, currentRemoteDir.getId()).stream().findFirst().orElseThrow(NoSuchElementException::new);
            pathSections.add(remoteDir.getId());
            currentRemoteDir = new Directory(remoteDir);
        }
        return Paths.get("root", pathSections.toArray(new String[0]));
    }

    public void crawlRemote() throws Exception {
        // Start with an empty tree, loaded from the specified JSON file
//        JsonObject tree = new Gson().fromJson(
//            new JsonReader(new InputStreamReader(
//                getClass().getResourceAsStream("base_tree.json")
//            )),
//            Directory.class
//        );
        Directory root = new Directory("13N1obnCJg-Bt3M5SOIKE8PSJvoHaCjNl", "client-root"); // TODO: Improve crawling algorithm (make it on-demand) and start from true root
        getSubfoldersSingleThread(root, drive);
//        ExecutorService threadPool = Executors.newFixedThreadPool(5);
//        getSubfoldersRecursive(root, drive, threadPool);
//        threadPool.awaitTermination(1, TimeUnit.MINUTES);   // TODO: Make sure this terminates properly, ie. THE ENTIRE remote filesystem has been crawled


        FileWriter writer = new FileWriter(outputFile.toFile());
        new Gson().toJson(root, Directory.class, new JsonWriter(writer));
        writer.close();
    }

    // FIXME: Gotta find a way to distribute search, waiting until entire filesystem is crawled, without going over limit.
    private void getSubfoldersRecursive(Directory directory, Drive drive, ExecutorService threadPool) throws ExecutionException, InterruptedException {
        Objects.requireNonNull(directory, "Directory may not be null");
        List<File> driveSubdirs = threadPool.submit(new SubdirLister(directory, drive)).get();
        System.out.printf("Subdirs of %s: %s\n", directory.getName(), driveSubdirs);
        for(File driveSubdir :  driveSubdirs) {
            Directory subdir = new Directory(driveSubdir.getId(), driveSubdir.getName());
            synchronized (directory.getSubdirs()) {
                directory.getSubdirs().add(subdir);
            }
            threadPool.submit(() -> {
                try {
                    Thread.sleep(100 * 5);  // Drive API limit is 1000req/100 sec, or 10req/sec. Add some delay to prevent going over limit.
                    getSubfoldersRecursive(subdir, drive, threadPool);
                } catch (Exception e) {
                    logger.error("Couldn't get subdirectories of {} ({}): {}", directory.getName(), directory.getId(), e.getStackTrace());
                }
            });
        }
    }

    private void getSubfoldersSingleThread(Directory directory, Drive drive) throws Exception {
        Objects.requireNonNull(directory, "Directory may not be null");
        Objects.requireNonNull(drive, "Drive service may not be null");
        List<File> driveSubdirs = new SubdirLister(directory, drive).call();
        System.out.printf("Subdirs of %s: %s\n", directory.getName(), driveSubdirs);
        for(File driveSubdir :  driveSubdirs) {
            Directory subdir = new Directory(driveSubdir.getId(), driveSubdir.getName());
            directory.getSubdirs().add(subdir);
            Thread.sleep(100);  // Drive API limit is 1000req/100 sec, or 10req/sec. Add some delay to prevent going over limit.
            getSubfoldersSingleThread(subdir, drive);
        }
    }

    // TODO: This can be changed back to a method
    private static final class SubdirLister implements Callable<List<File>> {
        private final Directory directory;
        private final Drive drive;
        private final Logger logger = LoggerFactory.getLogger(SubdirLister.class);

        SubdirLister(Directory dir, Drive drive) {
            Objects.requireNonNull(dir, "Directory may not be null");
            Objects.requireNonNull(dir.getId(), "Directory ID may not be null");
            Objects.requireNonNull(drive, "Drive service may not be null");
            this.directory = dir;
            this.drive = drive;
        }

        @Override
        public List<File> call() throws Exception {
            boolean firstPage = true;
            String nextPageToken = null;
            List<File> result = new ArrayList<>();
            while (firstPage || nextPageToken != null) {
                Drive.Files.List request = drive.files().list()
                        .setQ("mimeType='application/vnd.google-apps.folder' and '" + directory.getId() + "' in parents")
                        .setFields("nextPageToken,incompleteSearch,files(id,name)");
                if (!firstPage) {
                    request.setPageToken(nextPageToken);
                } else {
                    firstPage = false;
                }
                FileList response = request.execute();
                if (response.getIncompleteSearch()) {
                    logger.warn("WARNING: Searching subfolders of folder with ID {} yielded an incomplete search.", directory.getId());
                }
                result.addAll(response.getFiles());
                nextPageToken = response.getNextPageToken();
            }
            return result;
        }
    }
}
