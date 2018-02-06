package client;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Util {

    /**
     * Create a stream from an iterator.
     * @param iterator
     * @param <T>
     * @return
     * @see <a href="https://stackoverflow.com/a/21956917/2333689">StackOverflow source</a>
     */
    public static <T> Stream<T> streamFromIterator(Iterator<T> iterator) {
        Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator, 0);
        return StreamSupport.stream(spliterator, false);
    }
}
