package client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class that is loaded with ignore rules, and can then be asked whether a file should be ignored. Analogous to
 * .gitignore files.
 */
public class FileIgnorer {
    private final List<PathMatcher> rules;

    /**
     * Instances a new file ignorer with the specified rules.
     *
     * @param baseDir   Base directory from which to ignore. Will go down directories, but not up.
     * @param regexes     The ignore rules.
     * @throws java.util.regex.PatternSyntaxException On any invalid regex.
     */
    public FileIgnorer(Path baseDir, String... regexes) {
        if (!Files.isDirectory(baseDir)) {
            throw new IllegalArgumentException(baseDir + " is not a directory");
        }
        this.rules = Arrays.stream(regexes).map(rule -> {
            String regex = baseDir.toAbsolutePath().toString().replaceAll("\\\\", "\\\\\\\\") + baseDir.getFileSystem().getSeparator() + rule;  // Not using Paths.get() because a lot of regexes aren't valid filenames
            return baseDir.getFileSystem().getPathMatcher("regex:" + regex);
        }).collect(Collectors.toList());
    }

    /**
     * Convenience method. Calls {@link #FileIgnorer(Path, String...)} with the specified rules.
     *
     * @see #FileIgnorer(Path, String...)
     */
    public FileIgnorer(Path baseDir, List<String> regexes) {
        this(baseDir, regexes.toArray(new String[0]));
    }

    /**
     * Convenience method. Calls {@link #FileIgnorer(Path, String...)} with all the rules defined in the specified
     * {@code rulesFile}, with its containing directory as {@code baseDir}.
     *
     * @param rulesFile     The rules file.
     * @throws IOException  On I/O error while reading the rules file.
     */
    public FileIgnorer(Path rulesFile) throws IOException {
        this(rulesFile.getParent(), Files.readAllLines(rulesFile).toArray(new String[0]));
    }

    /**
     * Check whether a given path is ignored by any of the rules this ignorer was configured with, or with global rules.
     *
     * @param path  The path to test.
     * @return      Whether the path is ignored.
     */
    public boolean isIgnored(Path path) {
        // TODO: Benchmark whether parallel stream helps here
        return (this != Config.getInstance().getGlobalIgnorer() && isGloballyIgnored(path)) || rules.parallelStream().anyMatch(matcher -> matcher.matches(path.toAbsolutePath()));
    }

    /**
     * Check whether a path is ignored according to global ignore rules.
     *
     * @param path  The path to check.
     * @return      Whether the path is ignored by global rules.
     */
    private boolean isGloballyIgnored(Path path) {
        return Config.getInstance().getGlobalIgnorer().isIgnored(path);
    }
}
