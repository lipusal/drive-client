package client.discovery.traversal;

import client.DirectoryMapping;

/**
 * Interface to determine traversal strategy of directories.
 */
public interface TraversalStrategy {

    /**
     * Whether traversal is complete.
     *
     * @return Whether traversal is complete.
     */
    boolean isDone();

    /**
     * Get the next mapping.
     *
     * @return The next mapping.
     * @throws IllegalStateException If {@code isDone()}.
     */
    DirectoryMapping getNextMapping() throws IllegalStateException;

    /**
     * Add a submapping to be traversed.
     *
     * @param mapping The mapping to add.
     * @param parent  The parent mapping. May be {@code null} (eg. when adding root).
     * // TODO: Throw IllegalArgumentException if parent has not been added before?
     */
    void addSubmapping(DirectoryMapping mapping, DirectoryMapping parent);

    /**
     * Checks that traversal is not complete. Convenience method to be called as the first line of
     * {@link #getNextMapping()}.
     *
     * @throws IllegalStateException If done.
     */
    default void checkNotDone() throws IllegalStateException {
        if (isDone()) {
            throw new IllegalStateException(getClass().getSimpleName() + " traversal is already done, cannot add or remove submappings");
        }
    }
}
