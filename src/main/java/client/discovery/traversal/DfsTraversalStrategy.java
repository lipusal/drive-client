package client.discovery.traversal;

import client.DirectoryMapping;

import java.util.Deque;
import java.util.LinkedList;

/**
 * DFS traversal.
 */
public class DfsTraversalStrategy implements TraversalStrategy {
    protected Deque<DirectoryMapping> deque = new LinkedList<>();

    public DfsTraversalStrategy(DirectoryMapping rootMapping) {
        deque.addFirst(rootMapping);
    }

    @Override
    public boolean isDone() {
        return deque.isEmpty();
    }

    @Override
    public DirectoryMapping getNextMapping() throws IllegalStateException {
        return deque.removeFirst();
    }

    @Override
    public void addSubmapping(DirectoryMapping mapping, DirectoryMapping parent) throws IllegalStateException {
        deque.addFirst(mapping);
    }
}
