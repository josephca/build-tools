package com.buildcleaner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FileEntry {

	final String name;

	final String fullPath;

	final Optional<FileEntry> parent;

	final List<FileEntry> children = new ArrayList<>();

	final boolean directory;

	private long modifiedTime;

	public FileEntry(FileEntry parent, String name, String fullPath, boolean directory, long modifiedTime) {
		this.name = name;
		this.parent = Optional.ofNullable(parent);
		this.fullPath = fullPath;
		this.directory = directory;
		this.modifiedTime = modifiedTime;
	}

	public String getName() {
		return name;
	}

	public Optional<FileEntry> getParent() {
		return parent;
	}

	public boolean isDirectory() {
		return directory;
	}

	public String getFullPath() {

		return fullPath;

	}

	public List<FileEntry> getChildren() {
		return children;
	}

	public long getModifiedTime() {
		return modifiedTime;
	}

	public void setModifiedTime(long modifiedTime) {
		this.modifiedTime = modifiedTime;
	}

	@Override
	public String toString() {
		return fullPath;
	}
}
