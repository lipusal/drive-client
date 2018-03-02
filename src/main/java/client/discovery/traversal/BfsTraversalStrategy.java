package client.discovery.traversal;

import client.DirectoryMapping;

import java.util.Deque;
import java.util.LinkedList;

/**
 * BFS traversal.
 */
public class BfsTraversalStrategy implements TraversalStrategy {
    protected Deque<DirectoryMapping> deque = new LinkedList<>();

    public BfsTraversalStrategy(DirectoryMapping rootMapping) {
        deque.addLast(rootMapping);
    }

    @Override
    public boolean isDone() {
        return !deque.isEmpty();
    }

    @Override
    public DirectoryMapping getNextMapping() throws IllegalStateException {
        checkNotDone();
        return deque.removeFirst();
    }

    @Override
    public void addSubmapping(DirectoryMapping mapping, DirectoryMapping parent) throws IllegalStateException {
        deque.addLast(mapping);
    }
}
