package client;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class used to print a tree of a specified directory structure (via {@link DirectoryMapping}s) in ordered lists,
 * ie:
 * <ol>
 *     <li>a</li>
 *     <li>b
 *         <ol>
 *              <li>c</li>
 *          </ol>
 *     </li>
 *     <li>d</li>
 *     ...
 * </ol>
 * With this structure, allow mapping a user's text entry, eg. "2.1" to a corresponding directory (in this example, "c").
 */
public class DirectoryChooser {
    private final DirectoryMapping root;

    public DirectoryChooser(DirectoryMapping root) {
        this.root = root;
    }

    public String tree() {
        StringBuilder result = new StringBuilder();
        Deque<Integer> levels = new LinkedList<>();
        List<DirectoryMapping> rootSubdirs = alphabeticallySortedSubdirs(root);
        for (int i = 0; i < rootSubdirs.size(); i++) {
            levels.addFirst(i+1);
            result.append(branchRecursive(rootSubdirs.get(i), levels));
            levels.removeLast();
        }
        return result.toString();
    }

    /**
     * Get a directory mapping from the specified formatted input. Input must match "\d(\.\d)*", eg. "1" or "1.2.3", etc.
     *
     * @param input                     The formatted input.
     * @return                          The matching directory mapping.
     * @throws NoSuchElementException   If the input is formatted incorrectly, or if during any of the steps, a number
     *                                  is out of bounds of the current directory.
     * @throws NumberFormatException    If the input is not numeric.
     */
    public DirectoryMapping mappingFromInput(String input) throws NoSuchElementException {
        List<Integer> numbers = Arrays.stream(input.split("\\.")).map(numberStr -> Integer.parseInt(numberStr) - 1).collect(Collectors.toList());
        try {
            return findMapping(numbers);
        } catch (IndexOutOfBoundsException e) {
            throw new NoSuchElementException("No subdir matching subdir numbers " + input + " from " + root.getName());
        }
    }

    /**
     * Navigate through the directory structure from {@link #root} and enter in the specified subdirectory numbers until
     * out of numbers.
     *
     * @param dirNumbers                    Directory numbers to enter.
     * @return                              The final subdirectory.
     * @throws IndexOutOfBoundsException    If any directory number is out of bounds.
     */
    private DirectoryMapping findMapping(List<Integer> dirNumbers) throws IndexOutOfBoundsException {
        DirectoryMapping result = root;
        for (int dirNumber : dirNumbers) {
            result = alphabeticallySortedSubdirs(result).get(dirNumber);
        }
        return result;
    }

    private String branchRecursive(DirectoryMapping root, Deque<Integer> levels) {
        StringBuilder result = new StringBuilder();
        result
                .append('[').append(root.isSynced() ? 'X' : ' ').append("] ") // TODO: [*] if partially synced
                .append(levels.stream().map(Object::toString).collect(Collectors.joining("."))) // 1.2.3[...]
                .append(": ")
                .append(root.getName())
                .append('\n');
        if (root.isSynced()) {
            List<DirectoryMapping> sortedSubdirs = alphabeticallySortedSubdirs(root);
            for (int i = 0; i < sortedSubdirs.size(); i++) {
                for (int j = 0; j < levels.size(); j++) {
                    result.append('\t');
                }
                levels.addLast(i+1);
                result.append(branchRecursive(sortedSubdirs.get(i), levels));
                levels.removeLast();
            }
        }
        return result.toString();
    }

    /**
     * Get a sorted copy of alphabetically sorted subdirectories.
     *
     * @param parent    The parent whose subdirs to get.
     * @return          A <strong>copy</strong> of {@code parent}'s subdir list, sorted alphabetically.
     */
    private List<DirectoryMapping> alphabeticallySortedSubdirs(DirectoryMapping parent) {
        List<DirectoryMapping> sortedSubdirs = new ArrayList<>(parent.getSubdirs());
        sortedSubdirs.sort((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
        return sortedSubdirs;
    }
}
