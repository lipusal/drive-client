package client;

import com.google.api.services.drive.model.File;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Various utility methods, not necessarily related, that I couldn't think of anywhere else to put.
 */
public class Util {
    public static final String GOOGLE_DOC_MIME_TYPE = "application/vnd.google-apps.document",
        GOOGLE_SLIDES_MIME_TYPE = "application/vnd.google-apps.presentation",
        GOOGLE_SHEETS_MIME_TYPE = "application/vnd.google-apps.spreadsheet";

    private static final int[] illegalChars = {34, 60, 62, 124, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 58, 42, 63, 92, 47};
    static {
        Arrays.sort(illegalChars);
    }

    /**
     * Create a stream from an iterator.
     *
     * @param iterator  Iterator from which to create a stream.
     * @param <T>       Iterator (and resulting stream) type.
     * @return          The resulting stream.
     * @see <a href="https://stackoverflow.com/a/21956917/2333689">StackOverflow source</a>
     */
    public static <T> Stream<T> streamFromIterator(Iterator<T> iterator) {
        Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator, 0);
        return StreamSupport.stream(spliterator, false);
    }

    /**
     * @param remoteFile    The remote file.
     * @return              Whether the specified remote file is a directory.
     */
    public static boolean isDir(File remoteFile) {
        return remoteFile.getMimeType().equals(Config.DIRECTORY_MIME_TYPE);
    }

    /**
     * Checks whether the specified file is a Google document (Docs, Sheets or Slides). These files are not downloadable,
     * we must use their `webLink` if anything.
     *
     * @param remote    The remote file.
     * @return          Whether the file is a Google document.
     */
    public static boolean isGoogleDoc(File remote) {
        String mime = remote.getMimeType();
        return mime.equals(GOOGLE_DOC_MIME_TYPE) || mime.equals(GOOGLE_SLIDES_MIME_TYPE) || mime.equals(GOOGLE_SHEETS_MIME_TYPE);
    }

    /**
     * Tuple of arbitrary types.
     *
     * @param <A> Type of the first element of the tuple.
     * @param <B> Type of the second element of the tuple.
     */
    public static class Tuple<A, B> {
        private final A a;
        private final B b;

        public Tuple(A a, B b) {
            this.a = a;
            this.b = b;
        }

        public A getA() {
            return a;
        }

        public B getB() {
            return b;
        }
    }

    /**
     * Check whether the current operating system is Windows.
     *
     * @return Whether the current operating system is Windows.
     * @see <a href="https://www.mkyong.com/java/how-to-detect-os-in-java-systemgetpropertyosname/">Source</a>
     */
    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * Sanitize the given filename by removing characters disallowed on the current operating system.
     *
     * @param fileName Filename to sanitize. NOTE: <b>NOT</b> a full path, only a filename.
     * @return The sanitized filename.
     * @see <a href="https://stackoverflow.com/a/26420820/2333689>StackOverflow inspiration for Windows</a>.
     */
    public static String sanitizeFilename(String fileName) {
        if (!isWindows()) {
            return fileName;
        }
        fileName = fileName.trim();
        StringBuilder cleanName = new StringBuilder();
        int len = fileName.codePointCount(0, fileName.length());
        for (int i = 0; i < len; i++) {
            int c = fileName.codePointAt(i);
            if (Arrays.binarySearch(illegalChars, c) < 0) {
                cleanName.appendCodePoint(c);
            } else {
                cleanName.append('_');
            }
        }
        return cleanName.toString();
    }

    /**
     * Build a file reader that enforces UTF-8 encoding.
     *
     * @param path Path to build a reader for.
     * @return UTF-8 reader.
     * @throws FileNotFoundException If file does not exist.
     * @see #utf8FileWriter(Path)
     */
    public static Reader utf8FileReader(Path path) throws FileNotFoundException {
        return new InputStreamReader(new FileInputStream(path.toFile()), StandardCharsets.UTF_8);
    }

    /**
     * Build a file writer that enforces UTF-8 encoding.
     *
     * @param path Path to build a writer for.
     * @return UTF-8 writer.
     * @throws FileNotFoundException If file does not exist.
     * @see #utf8FileReader(Path)
     */
    public static Writer utf8FileWriter(Path path) throws FileNotFoundException {
        return new OutputStreamWriter(new FileOutputStream(path.toFile()), StandardCharsets.UTF_8);
    }
}
