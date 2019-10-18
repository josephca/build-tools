package com.buildcleaner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.IdentityRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.agentproxy.AgentProxyException;
import com.jcraft.jsch.agentproxy.Connector;
import com.jcraft.jsch.agentproxy.ConnectorFactory;
import com.jcraft.jsch.agentproxy.RemoteIdentityRepository;

public class Main {

	private static final boolean USE_SSH_AGENT = true; // Set to false for testing purposes
	private static final String NON_SSH_AGENT_PASSWORD = ""; // Set this if USE_SSH_AGENT is false.

	private static boolean DELETION_ENABLED = false;

	public static void main(String[] args) throws AgentProxyException, JSchException, SftpException, JsonParseException,
			JsonMappingException, IOException {

		if (DELETION_ENABLED == true) {
			throw new RuntimeException(
					"Don't set DELETION_ENABLED field to true in the source code, use the environment variable.");
		}

		String enableDeleteValue = System.getenv().entrySet().stream()
				.filter(e -> e.getKey().equalsIgnoreCase("ENABLE_DELETE")).map(e -> e.getValue()).findFirst()
				.orElse(null);

		boolean deleteEnabled = enableDeleteValue != null && enableDeleteValue.equalsIgnoreCase("true");

		if (args.length != 1) {

			System.out.println("(path to yaml file)");
			return;
		}

		if (!deleteEnabled) {
			System.out.println("* Deletion is disabled, enable it with ENABLE_DELETE=true environment variable.");
		}

		DELETION_ENABLED = deleteEnabled;

		File yamlFile = new File(args[0]);

//		File yamlFile = new File("C:\\Users\\JONATHANWest\\git\\build-cleaner\\BuildCleaner\\test.yaml");

		if (!yamlFile.exists()) {
			System.err.println("File does not exist: " + yamlFile.getPath());
			return;
		}

		ObjectMapper om = new ObjectMapper(new YAMLFactory());
		SetupYaml yaml = om.readValue(yamlFile, SetupYaml.class);
		{

			if (yaml.getHostname() == null || yaml.getHostname().trim().isEmpty()) {
				System.err.println("Missing hostname");
				return;
			}

			if (yaml.getUsername() == null || yaml.getUsername().trim().isEmpty()) {
				System.err.println("Missing username");
				return;
			}
			if (yaml.getNumberOfBuildsToPreserve() == null || yaml.getNumberOfBuildsToPreserve() == 0) {
				System.err.println("Invalid number of builds to preserve: " + yaml.getNumberOfBuildsToPreserve());
				return;
			}

			if (yaml.getNumberOfDaysToPreserve() == null || yaml.getNumberOfDaysToPreserve() == 0) {
				System.err.println("Invalid number of days to preserve: " + yaml.getNumberOfDaysToPreserve());
				return;
			}

		}

		ConnectResult cr = Main.connect(yaml.getUsername(), yaml.getHostname());

		System.out.println("Deleting builds by number:");
		{
			List<FileEntry> deleteByNumber = new ArrayList<>();
			for (String path : yaml.getPathsDeleteByNumber()) {
				FileEntry curr = getRecursiveDirectory(path, cr.getChannel());
				deleteByNumber.add(curr);
			}

			deleteByNumber.forEach(e -> deleteByNumber(e, yaml, cr.getChannel()));
		}
		System.out.println();

		System.out.println("Deleting builds by timestamp:");
		List<FileEntry> deleteByTimestamp = new ArrayList<>();
		{
			for (String path : yaml.getPathsDeleteByTimestamp()) {
				FileEntry curr = getRecursiveDirectory(path, cr.getChannel());
				deleteByTimestamp.add(curr);
			}

			deleteByTimestamp.forEach(e -> deleteByTimestamp(e, yaml, cr.getChannel()));
		}

	}

	// For directories that we want to preserve/delete by number...
	//
	// For any paths specified under 'pathsDeleteByNumber' in the setup YAML:
	// - Example:
	// /home/data/httpd/download.eclipse.org/codewind/codewind-eclipse/master
	// - (https://download.eclipse.org/codewind/codewind-eclipse/master/)
	//
	// Algorithm is:
	//
	// 1) Sort the directories under the path
	// (/home/data/httpd/download.eclipse.org/codewind/codewind-eclipse/master) by
	// number.
	//
	// 2) Delete the directories that are not part of the set of largest X numbers.
	// where X is 'pathsDeleteByNumber'
	private static void deleteByNumber(FileEntry fileEntry, SetupYaml yaml, ChannelSftp channelSftp) {

		List<FileEntry> children = findBuildDirectoriesSortedByNumber(fileEntry);

		List<FileEntry> toDelete = new ArrayList<>();

		while (children.size() > yaml.getNumberOfBuildsToPreserve()) {
			toDelete.add(children.remove(0));
		}

		System.out.println();
		for (FileEntry delete : toDelete) {

			System.out.println();
			printDelete(delete);

			String parentRoot = delete.getFullPath();

			List<FileEntry> pathsToDelete = generateOrderedPathsToDelete(delete);
			pathsToDelete.forEach(e -> DESTRUCTIVE_deletePath(e, channelSftp, parentRoot));

		}

	}

	// For directories that we want to preserve/delete by timestamp...
	//
	// For any paths specified under 'pathsDeleteByTimestamp' in the setup YAML:
	// - Example: /home/data/httpd/download.eclipse.org/codewind/codewind-eclipse/pr
	// - (https://download.eclipse.org/codewind/codewind-eclipse/pr/)
	//
	// Algorithm is:
	//
	// 1) Sort the directories under the path
	// (/home/data/httpd/download.eclipse.org/codewind/codewind-eclipse/pr) by
	// modified timestamp.
	//
	// 2) Delete all the directories (pull requests) that are older than X days old
	// (where X is specified in the YAML)
	private static void deleteByTimestamp(FileEntry fileEntry, SetupYaml yaml, ChannelSftp channelSftp) {

		List<FileEntry> children = findBuildDirectoriesSortedByTimestamp(fileEntry);

		long deleteBefore = System.currentTimeMillis()
				- TimeUnit.MILLISECONDS.convert(yaml.getNumberOfDaysToPreserve(), TimeUnit.DAYS);

		List<FileEntry> toDelete = children.stream().filter(e -> e.getModifiedTime() < deleteBefore)
				.collect(Collectors.toList());

		for (FileEntry delete : toDelete) {

			System.out.println();
			printDelete(delete);
			String parentRoot = delete.getFullPath();

			List<FileEntry> pathsToDelete = generateOrderedPathsToDelete(delete);
			pathsToDelete.forEach(e -> DESTRUCTIVE_deletePath(e, channelSftp, parentRoot));
		}

	}

	/**
	 * Delete the file specified by 'e', if DELETION_ENABLED is true. Be VERY VERY
	 * VERY careful when making ANY changes to this method.
	 */
	private static void DESTRUCTIVE_deletePath(final FileEntry e, final ChannelSftp channelSftp,
			final String parentRoot_dontDeleteMe) {

		if (e == null) {
			throw new RuntimeException("* ERROR: FileEntry is null!");
		}

		final String path = e.getFullPath();

		if (path == null) {
			throw new RuntimeException("Path is null?");
		}

		// Sanity test to prevent accidentally deleting files (every possible error
		// condition I could think of)
		{

			// BE VERY VERY CAREFUL WHEN MAKING CHANGES TO ANYTHING INSIDE THIS BLOCK!!!

			boolean tooShortOrWrongRoot = path.trim().length() < 40
					|| !path.startsWith("/home/data/httpd/download.eclipse.org/codewind/codewind");

			// The path to delete must container /pr/ or /master/
			boolean containsPRorMaster = path.contains("/pr/") || path.contains("/master/");

			// We should never delete paths that contain any of these strings/characters
			boolean containsBadStrings = path.contains("./") || path.contains(" ") || path.contains("\n")
					|| path.contains("%") || path.contains("&") || path.contains("\"") || path.contains("'");

			// The path to delete should always start with the parent's fullPath
			boolean containsParentRoot = path.startsWith(parentRoot_dontDeleteMe);

			// The path must contain at LEAST one directory that is only a number.
			//
			// Value will be true, for this:
			// /home/data/httpd/download.eclipse.org/codewind/codewind-installer/master/26/some=file
			// (because of /26/)
			//
			// Value will be false, for this:
			// /home/data/httpd/download.eclipse.org/codewind/codewind-installer/master
			// (because there is no numbered directory)
			boolean containsNumericalDirectory = Arrays.asList(path.split("/")).stream().anyMatch(f -> isNumber(f));

			if (tooShortOrWrongRoot || containsBadStrings || !containsPRorMaster || !containsParentRoot
					|| !containsNumericalDirectory) {

				// BE VERY VERY CAREFUL WHEN MAKING CHANGES TO THE ABOVE IF STATEMENT, OR
				// ANYTHING INSIDE THIS BLOCK!!!!

				throw new RuntimeException("SEVERE: Bad path found" + e.getFullPath());

			}
		}

		System.out.println("- " + path + " " + (e.isDirectory() ? "[DIR]" : ""));
		if (DELETION_ENABLED) {
			// THIS IS WHERE DELETE ACTUALLY HAPPENS; ALWAYS ENSURE THAT 'path' IS COMING
			// DIRECTLY FROM THE FILEENTRY AT THE TOP OF THE METHOD, AND THAT PATH IS A
			// FULLPATH.

			try {
				if (e.isDirectory()) {
					channelSftp.rmdir(path);
				} else {
					channelSftp.rm(path);
				}
			} catch (SftpException e1) {
				throw new RuntimeException(e1);
			}

		}
	}

	private static void printDelete(FileEntry toDelete) {
		System.out.println("* Deleting " + toDelete.getFullPath() + " ("
				+ TimeUnit.DAYS.convert(System.currentTimeMillis() - toDelete.getModifiedTime(), TimeUnit.MILLISECONDS)
				+ " days old)");

	}

	/** Return child file entries, sorted ascending by last modification time */
	private static List<FileEntry> findBuildDirectoriesSortedByTimestamp(FileEntry fileEntry) {

		return fileEntry.getChildren().stream().filter(f -> !f.getName().equals("latest"))
				.filter(f -> isNumber(f.getName())).sorted((a, b) -> {
					// Sort ascending by modification time
					long l = a.getModifiedTime() - b.getModifiedTime();
					if (l > 0) {
						return 1;
					} else if (l < 0) {
						return -1;
					} else {
						return 0;
					}
				}).collect(Collectors.toList());

	}

	/** Return child file entries, sorted ascending by build number */
	private static List<FileEntry> findBuildDirectoriesSortedByNumber(FileEntry fileEntry) {

		return fileEntry.getChildren().stream().filter(f -> !f.getName().equals("latest"))
				.filter(f -> isNumber(f.getName()))
				.sorted((a, b) -> (Integer.parseInt(a.getName()) - Integer.parseInt(b.getName())))
				.collect(Collectors.toList());

	}

	private static List<FileEntry> generateOrderedPathsToDelete(FileEntry fileEntry) {

		ArrayList<FileEntry> allEntries = new ArrayList<>();

		// Recursively walk through all the FileEntry objects
		{
			List<FileEntry> queue = new ArrayList<>();
			queue.add(fileEntry);
			while (queue.size() > 0) {
				FileEntry curr = queue.remove(0);

				if (curr.isDirectory()) {

					curr.getChildren().forEach(e -> {
						queue.add(e);
					});

				}
				allEntries.add(curr);
			}
		}

		List<FileEntry> paths = allEntries.stream().sorted(new Comparator<FileEntry>() {

			// Count the # of forward slashes in the string
			private int countSlashes(String str) {
				return (int) str.chars().mapToObj(e -> (char) e).filter(e -> e == '/').count();

			}

			@Override
			public int compare(FileEntry a, FileEntry b) {
				// Sort descending
				return countSlashes(b.getFullPath()) - countSlashes(a.getFullPath());
			}
		}).collect(Collectors.toList());

		return paths;
	}

	private static boolean isNumber(String str) {
		try {
			Integer.parseInt(str);
			return true;
		} catch (NumberFormatException nfe) {
			return false;
		}

	}

	@SuppressWarnings("unchecked")
	private static FileEntry getRecursiveDirectory(String initialPath, ChannelSftp channel) throws SftpException {

		// Strip trailing slash
		while (initialPath.endsWith("/")) {
			initialPath = initialPath.substring(0, initialPath.length() - 1);
		}

		// Recursively walk a directory tree
		String path = initialPath;

		FileEntry result;

		FileEntry parent;
		{

			String filename = path.substring(path.lastIndexOf("/"));
			parent = new FileEntry(null, filename, path, true, 0);
			result = parent;
		}

		List<FileEntry> queue = new ArrayList<>();

		do {

			{
				List<LsEntry> list = Collections.emptyList();
				try {
					list = new ArrayList<LsEntry>(channel.ls(path));
				} catch (Exception e) {
					if (e.getMessage().contains("Permission denied")) {
						System.err.println("Permission denied on:" + path);
					} else {
						throw new RuntimeException("Error on path: " + path, e);
					}
				}
				for (LsEntry f : list) {

					if (f.getFilename().equals(".") || f.getFilename().equals("..")) {
						continue;
					}

					boolean isDir = f.getAttrs().isDir();

					FileEntry fe = new FileEntry(parent, f.getFilename(), path + "/" + f.getFilename(), isDir,
							1000l * ((long) f.getAttrs().getMTime()));

					if (isDir) {
						queue.add(fe);
					}

					parent.getChildren().add(fe);

				}

			}

			if (queue.size() > 0) {
				parent = queue.remove(0);
				path = parent.getFullPath();
			} else {
				path = null;
				parent = null;
			}

		} while (path != null);

		return result;
	}

	private static ConnectResult connect(String user, String host) throws AgentProxyException, JSchException {
		JSch jsch = new JSch();

		if (USE_SSH_AGENT) {
			Connector con = null;
			try {
				ConnectorFactory cf = ConnectorFactory.getDefault();
				con = cf.createConnector();
			} catch (AgentProxyException e) {
				throw e;
			}

			if (con != null) {
				IdentityRepository irepo = new RemoteIdentityRepository(con);
				jsch.setIdentityRepository(irepo);
			}

			Session session = jsch.getSession(user, host, 22);
			session.setDaemonThread(true);
			session.setConfig("StrictHostKeyChecking", "no");
			session.connect();

			Channel channel = session.openChannel("sftp");
			((ChannelSftp) channel).setAgentForwarding(true);

			channel.connect();

			return new ConnectResult(session, (ChannelSftp) channel);

		} else {

			Session session = jsch.getSession(user, host, 22);
			session.setPassword(NON_SSH_AGENT_PASSWORD);
			session.setDaemonThread(true);
			session.setConfig("StrictHostKeyChecking", "no");
			session.connect();

			Channel channel = session.openChannel("sftp");
			((ChannelSftp) channel).setAgentForwarding(true);

			channel.connect();

			return new ConnectResult(session, (ChannelSftp) channel);

		}
	}

	private static boolean pathContainsNumericalDirectory(String path) {
		return Arrays.asList(path.split("/")).stream().anyMatch(e -> isNumber(e));
	}

	public static class ConnectResult {

		private final Session session;
		private final ChannelSftp channel;

		public ConnectResult(Session session, ChannelSftp channel) {
			this.session = session;
			this.channel = channel;
		}

		public final Session getSession() {
			return session;
		}

		public ChannelSftp getChannel() {
			return channel;
		}
	}

}
