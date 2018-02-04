package client;

import com.google.api.services.drive.model.File;

import java.util.*;
import java.util.stream.Collectors;

public class Directory {
    private String id;
    private String name;
    private List<Directory> subdirs = new ArrayList<>();

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

    public Directory(File remoteDirectory) {
        this(remoteDirectory.getId(), remoteDirectory.getName());
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

    public List<Directory> getSubdirByName(String name) {
        return subdirs.stream().filter(directory -> directory.name.equals(name)).collect(Collectors.toList());
    }

    public Optional<Directory> getSubdirById(String id) {
        return subdirs.stream().filter(directory -> directory.id.equals(id)).findFirst();
    }

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Directory directory = (Directory) o;
        if ((id == null && directory.id != null) || (id != null && directory.id == null)) {
            // If exactly one of the two doesn't have an ID set, compare only names
            return Objects.equals(name, directory.name);
        } else {
            return
                    Objects.equals(id, directory.id) &&
                    Objects.equals(name, directory.name);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
}
