package com.buildcleaner;

import java.util.ArrayList;
import java.util.List;

public class SetupYaml {

	String username;

	String hostname;

	Integer numberOfBuildsToPreserve;

	Integer numberOfDaysToPreserve;

	List<String> pathsDeleteByNumber = new ArrayList<>();

	List<String> pathsDeleteByTimestamp = new ArrayList<>();

	public Integer getNumberOfBuildsToPreserve() {
		return numberOfBuildsToPreserve;
	}

	public void setNumberOfBuildsToPreserve(Integer numberOfBuildsToPreserve) {
		this.numberOfBuildsToPreserve = numberOfBuildsToPreserve;
	}

	public Integer getNumberOfDaysToPreserve() {
		return numberOfDaysToPreserve;
	}

	public void setNumberOfDaysToPreserve(Integer numberOfDaysToPreserve) {
		this.numberOfDaysToPreserve = numberOfDaysToPreserve;
	}

	public List<String> getPathsDeleteByNumber() {
		return pathsDeleteByNumber;
	}

	public void setPathsDeleteByNumber(List<String> pathsDeleteByNumber) {
		this.pathsDeleteByNumber = pathsDeleteByNumber;
	}

	public List<String> getPathsDeleteByTimestamp() {
		return pathsDeleteByTimestamp;
	}

	public void setPathsDeleteByTimestamp(List<String> pathsDeleteByTimestamp) {
		this.pathsDeleteByTimestamp = pathsDeleteByTimestamp;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

}
