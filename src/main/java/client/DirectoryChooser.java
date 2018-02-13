package client;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
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

    @Override
    public String  toString() {
        StringBuilder result = new StringBuilder();
        Deque<Integer> levels = new LinkedList<>();
        List<DirectoryMapping> rootSubdirs = root.getSubdirs();
        for (int i = 0; i < rootSubdirs.size(); i++) {
            levels.addFirst(i+1);
            result.append(branchRecursive(rootSubdirs.get(i), levels));
            levels.removeLast();
        }
        return result.toString();
    }

    private String branchRecursive(DirectoryMapping root, Deque<Integer> levels) {
        StringBuilder result = new StringBuilder();
        result
                .append('[').append(root.isSynced() ? 'X' : "").append("] ") // TODO: [*] if partially synced
                .append(levels.stream().map(Object::toString).collect(Collectors.joining("."))) // 1.2.3[...]
                .append(" - ")
                .append(root.getName())
                .append('\n');
        if (root.isSynced()) {
            for (int i = 0; i < root.getSubdirs().size(); i++) {
                for (int j = 0; j < levels.size(); j++) {
                    result.append('\t');
                }
                levels.addLast(i+1);
                result.append(branchRecursive(root.getSubdirs().get(i), levels));
                levels.removeLast();
            }
        }
        return result.toString();
    }
}
