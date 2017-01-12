/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.jenkins.results.parser;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import java.net.MalformedURLException;
import java.net.URISyntaxException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;

import org.json.JSONObject;

/**
 * @author Peter Yoo
 */
public class AxisBuild extends BaseBuild {

	@Override
	public void findDownstreamBuilds() {
	}

	@Override
	public String getAppServer() {
		Build parentBuild = getParentBuild();

		return parentBuild.getAppServer();
	}

	@Override
	public String getArchivePath() {
		if (archiveName == null) {
			System.out.println(
				"Build URL " + getBuildURL() + " has a null archive name");
		}

		StringBuilder sb = new StringBuilder(archiveName);

		if (!archiveName.endsWith("/")) {
			sb.append("/");
		}

		sb.append(getMaster());
		sb.append("/");
		sb.append(getJobName());
		sb.append("/");
		sb.append(getAxisVariable());
		sb.append("/");
		sb.append(getBuildNumber());

		return sb.toString();
	}

	public String getAxisNumber() {
		Matcher matcher = _axisVariablePattern.matcher(getAxisVariable());

		if (matcher.find()) {
			return matcher.group("axisNumber");
		}

		throw new RuntimeException(
			"Invalid axis variable: " + getAxisVariable());
	}

	public String getAxisVariable() {
		return axisVariable;
	}

	@Override
	public String getBrowser() {
		Build parentBuild = getParentBuild();

		return parentBuild.getBrowser();
	}

	@Override
	public String getBuildURL() {
		String jobURL = getJobURL();
		int buildNumber = getBuildNumber();

		if ((jobURL == null) || (buildNumber == -1)) {
			return null;
		}

		if (fromArchive) {
			return jobURL + "/" + axisVariable + "/" + buildNumber + "/";
		}

		try {
			jobURL = JenkinsResultsParserUtil.decode(jobURL);
		}
		catch (UnsupportedEncodingException uee) {
			throw new RuntimeException("Unable to decode " + jobURL, uee);
		}

		String buildURL = jobURL + "/" + axisVariable + "/" + buildNumber + "/";

		try {
			return JenkinsResultsParserUtil.encode(buildURL);
		}
		catch (MalformedURLException murle) {
			throw new RuntimeException("Could not encode " + buildURL, murle);
		}
		catch (URISyntaxException urise) {
			throw new RuntimeException("Could not encode " + buildURL, urise);
		}
	}

	@Override
	public String getBuildURLRegex() {
		StringBuffer sb = new StringBuffer();

		sb.append("http[s]*:\\/\\/");
		sb.append(JenkinsResultsParserUtil.getRegexLiteral(getMaster()));
		sb.append("[^\\/]*");
		sb.append("[\\/]+job[\\/]+");

		String jobNameRegexLiteral = JenkinsResultsParserUtil.getRegexLiteral(
			getJobName());

		jobNameRegexLiteral = jobNameRegexLiteral.replace("\\(", "(\\(|%28)");
		jobNameRegexLiteral = jobNameRegexLiteral.replace("\\)", "(\\)|%29)");

		sb.append(jobNameRegexLiteral);

		sb.append("[\\/]+");
		sb.append(JenkinsResultsParserUtil.getRegexLiteral(getAxisVariable()));
		sb.append("[\\/]+");
		sb.append(getBuildNumber());
		sb.append("[\\/]*");

		return sb.toString();
	}

	@Override
	public String getDatabase() {
		Build parentBuild = getParentBuild();

		return parentBuild.getDatabase();
	}

	@Override
	public String getDisplayName() {
		StringBuilder sb = new StringBuilder();

		sb.append(getAxisVariable());
		sb.append(" #");
		sb.append(getBuildNumber());

		return sb.toString();
	}

	@Override
	public Element getGitHubMessage() {
		String status = getStatus();

		if (!status.equals("completed") && (getParentBuild() != null)) {
			return null;
		}

		String result = getResult();

		if (result.equals("SUCCESS")) {
			return null;
		}

		Element messageElement = new DefaultElement("div");

		Dom4JUtil.getNewAnchorElement(
			getBuildURL(), messageElement, getDisplayName());

		if (result.equals("ABORTED")) {
			messageElement.add(
				Dom4JUtil.toCodeSnippetElement("Build was aborted"));
		}

		if (result.equals("FAILURE")) {
			Element failureMessageElement = getFailureMessageElement();

			if (failureMessageElement != null) {
				messageElement.add(failureMessageElement);
			}
		}

		if (result.equals("UNSTABLE")) {
			String jobVariant = getParameterValue("JOB_VARIANT");

			Element downstreamBuildOrderedListElement = Dom4JUtil.getNewElement(
				"ol", messageElement);

			int failureCount = 0;

			for (TestResult testResult : getTestResults(null)) {
				String testStatus = testResult.getStatus();

				if (testStatus.equals("PASSED") ||
					testStatus.equals("SKIPPED")) {

					continue;
				}

				Element downstreamBuildListItemElement =
					Dom4JUtil.getNewElement(
						"li", downstreamBuildOrderedListElement);

				if (failureCount < 3) {
					downstreamBuildListItemElement.add(
						Dom4JUtil.getNewAnchorElement(
							testResult.getTestReportURL(),
							testResult.getDisplayName()));

					if (jobVariant.contains("functional")) {
						Dom4JUtil.addToElement(
							downstreamBuildListItemElement, " - ",
							Dom4JUtil.getNewAnchorElement(
								testResult.getPoshiReportURL(), "Poshi Report"),
							" - ",
							Dom4JUtil.getNewAnchorElement(
								testResult.getPoshiSummaryURL(),
								"Poshi Summary"),
							" - ",
							Dom4JUtil.getNewAnchorElement(
								testResult.getConsoleOutputURL(),
								"Console Output"));

						if (testResult.hasLiferayLog()) {
							Dom4JUtil.addToElement(
								downstreamBuildListItemElement, " - ",
								Dom4JUtil.getNewAnchorElement(
									testResult.getLiferayLogURL(),
									"Liferay Log"));
						}
					}

					failureCount++;

					continue;
				}

				downstreamBuildListItemElement.addText("...");

				break;
			}
		}

		return messageElement;
	}

	@Override
	public String getJDK() {
		Build parentBuild = getParentBuild();

		return parentBuild.getJDK();
	}

	@Override
	public String getOperatingSystem() {
		Build parentBuild = getParentBuild();

		return parentBuild.getOperatingSystem();
	}

	public String getTestRayLogsURL() {
		StringBuilder sb = new StringBuilder();

		TopLevelBuild topLevelBuild = getTopLevelBuild();

		Properties buildProperties = null;

		try {
			buildProperties = JenkinsResultsParserUtil.getBuildProperties();
		}
		catch (IOException ioe) {
			throw new RuntimeException("Unable to get build properties", ioe);
		}

		String logBaseURL = null;

		if (buildProperties.containsKey("log.base.url")) {
			logBaseURL = buildProperties.getProperty("log.base.url");
		}

		if (logBaseURL == null) {
			logBaseURL = defaultLogBaseURL;
		}

		sb.append(logBaseURL);
		sb.append("/");
		sb.append(topLevelBuild.getMaster());
		sb.append("/");

		Map<String, String> startPropertiesTempMap =
			getStartPropertiesTempMap();

		sb.append(startPropertiesTempMap.get("TOP_LEVEL_START_TIME"));

		sb.append("/");
		sb.append(topLevelBuild.getJobName());
		sb.append("/");
		sb.append(topLevelBuild.getBuildNumber());
		sb.append("/");
		sb.append(getParameterValue("JOB_VARIANT"));
		sb.append("/");
		sb.append(getAxisNumber());

		return sb.toString();
	}

	@Override
	public List<TestResult> getTestResults(String testStatus) {
		String status = getStatus();

		if (!status.equals("completed")) {
			return Collections.emptyList();
		}

		JSONObject testReportJSONObject = getTestReportJSONObject();

		return TestResult.getTestResults(
			this, testReportJSONObject.getJSONArray("suites"), testStatus);
	}

	@Override
	public void reinvoke() {
		throw new RuntimeException("Axis builds cannot be reinvoked");
	}

	protected AxisBuild(String url) {
		this(url, null);
	}

	protected AxisBuild(String url, BatchBuild parentBuild) {
		super(JenkinsResultsParserUtil.getLocalURL(url), parentBuild);
	}

	@Override
	protected void checkForReinvocation() {
	}

	@Override
	protected FailureMessageGenerator[] getFailureMessageGenerators() {
		return _failureMessageGenerators;
	}

	@Override
	protected Element getGitHubMessageJobResultsElement() {
		return null;
	}

	@Override
	protected String getStopPropertiesTempMapURL() {
		if (fromArchive) {
			return getBuildURL() + "/stop-properties.json";
		}

		TopLevelBuild topLevelBuild = getTopLevelBuild();

		StringBuilder sb = new StringBuilder();

		sb.append(
			"http://cloud-10-0-0-31.lax.liferay.com/osb-jenkins-web/map/");
		sb.append(topLevelBuild.getMaster());
		sb.append("/");
		sb.append(topLevelBuild.getJobName());
		sb.append("/");
		sb.append(topLevelBuild.getBuildNumber());
		sb.append("/");
		sb.append(getJobName());
		sb.append("/");
		sb.append(getAxisVariable());
		sb.append("/");
		sb.append(getParameterValue("JOB_VARIANT"));
		sb.append("/");
		sb.append("stop.properties");

		return sb.toString();
	}

	@Override
	protected void setBuildURL(String buildURL) {
		try {
			buildURL = JenkinsResultsParserUtil.decode(buildURL);
		}
		catch (UnsupportedEncodingException uee) {
			throw new IllegalArgumentException(
				"Unable to decode " + buildURL, uee);
		}

		try {
			String archiveMarkerContent = JenkinsResultsParserUtil.toString(
				buildURL + "/archive-marker", false, 0, 0, 0);

			if ((archiveMarkerContent != null) &&
				!archiveMarkerContent.isEmpty()) {

				fromArchive = true;
			}
			else {
				fromArchive = false;
			}
		}
		catch (IOException ioe) {
			fromArchive = false;
		}

		Matcher matcher = buildURLPattern.matcher(buildURL);

		if (!matcher.find()) {
			matcher = archiveBuildURLPattern.matcher(buildURL);

			if (!matcher.find()) {
				throw new IllegalArgumentException(
					"Invalid build URL " + buildURL);
			}

			archiveName = matcher.group("archiveName");
		}

		axisVariable = matcher.group("axisVariable");
		jobName = matcher.group("jobName");
		master = matcher.group("master");

		setBuildNumber(Integer.parseInt(matcher.group("buildNumber")));

		loadParametersFromBuildJSONObject();

		setStatus("running");
	}

	protected static final Pattern archiveBuildURLPattern = Pattern.compile(
		"(\\$\\{dependencies\\.url\\}|file:|http://).*/(?<archiveName>[^/]+)/" +
			"(?<master>[^/]+)/+(?<jobName>[^/]+)/(?<axisVariable>" +
				"AXIS_VARIABLE=[^,]+,[^/]+)/(?<buildNumber>\\d+)/?");
	protected static final Pattern buildURLPattern = Pattern.compile(
		"\\w+://(?<master>[^/]+)/+job/+(?<jobName>[^/]+)/" +
			"(?<axisVariable>AXIS_VARIABLE=[^,]+,[^/]+)/" +
				"(?<buildNumber>\\d+)/?");
	protected static final String defaultLogBaseURL =
		"https://testray.liferay.com/reports/production/logs";

	protected String axisVariable;

	private static final Pattern _axisVariablePattern = Pattern.compile(
		"AXIS_VARIABLE=(?<axisNumber>[^,]+),.*");

	private static final FailureMessageGenerator[] _failureMessageGenerators = {
		new IntegrationTestTimeoutFailureMessageGenerator(),
		new LocalGitMirrorFailureMessageGenerator(),
		new PluginFailureMessageGenerator(),
		new PluginGitIDFailureMessageGenerator(),
		new SemanticVersioningFailureMessageGenerator(),
		new SourceFormatFailureMessageGenerator(),

		new GenericFailureMessageGenerator()
	};

}