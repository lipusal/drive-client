package client;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Directory {
    private String id;
    private String name;
    private List<Directory> subdirs = Collections.emptyList();

    public Directory(String id, String name, Directory... subdirs) {
        this.id = id;
        this.name = name;
        this.subdirs.addAll(Arrays.asList(subdirs));
    }

    public Directory(String name, Directory... subdirs) {
        this(null, name, subdirs);
    }

    public Directory(String name) {
        this(name, new Directory[0]);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<Directory> getSubdirs() {
        return subdirs;
    }
}
