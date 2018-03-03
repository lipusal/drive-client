package client;

import com.google.api.services.drive.model.File;

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
}
