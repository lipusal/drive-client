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
}
