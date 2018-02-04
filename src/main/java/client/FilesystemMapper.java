package client;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

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
                    System.err.format("Couldn't get subdirectories of %s (%s)", directory.getName(), directory.getId());
                    e.printStackTrace();
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
                    System.err.println("WARNING: Searching subfolders of folder with ID" + directory.getId() + " yielded an incomplete search.");
                }
                result.addAll(response.getFiles());
                nextPageToken = response.getNextPageToken();
            }
            return result;
        }
    }
}
