/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.meterware.httpunit.*;
import java.io.*;
import java.net.*;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.ServletTestingSupport;
import org.eclipse.orion.server.git.GitConstants;
import org.json.*;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.SAXException;

public class GitFetchTest extends GitTest {
	@Test
	public void testFetch() throws IOException, SAXException, JSONException, URISyntaxException {
		// add clone
		URIish uri = new URIish(gitDir.toURL());
		String name = null;
		WebRequest request = GitCloneTest.getPostGitCloneRequest(uri, name);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String taskLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(taskLocation);
		String cloneLocation = waitForCloneCompletion(taskLocation);

		//validate the clone metadata
		response = webConversation.getResponse(getCloneRequest(cloneLocation));
		String location = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(location);
		JSONObject clone = new JSONObject(response.getText());
		String contentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(contentLocation);

		URI workspaceLocation = createWorkspace(getMethodName());

		// link
		ServletTestingSupport.allowedPrefixes = contentLocation;
		String projectName = getMethodName();
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentLocation);
		InputStream in = new StringBufferInputStream(body.toString());
		// http://<host>/workspace/<workspaceId>/
		request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.optString(GitConstants.KEY_REMOTE, null);
		assertNotNull(gitRemoteUri);

		// list remotes
		request = GitRemoteTest.getGetGitRemoteRequest(gitRemoteUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject remotes = new JSONObject(response.getText());
		JSONArray remotesArray = remotes.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, remotesArray.length());
		JSONObject remote = remotesArray.getJSONObject(0);
		assertNotNull(remote);
		assertEquals(Constants.DEFAULT_REMOTE_NAME, remote.getString(ProtocolConstants.KEY_NAME));
		String remoteLocation = remote.getString(ProtocolConstants.KEY_LOCATION);
		assertNotNull(remoteLocation);

		// get remote details
		JSONObject details = getRemoteBranch(remoteLocation);
		String refId = details.getString(ProtocolConstants.KEY_ID);
		String remoteBranchLocation = details.getString(ProtocolConstants.KEY_LOCATION);

		// fetch
		request = getPostGitRemoteRequest(remoteBranchLocation, true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// get remote details again
		String newRefId = getRemoteBranch(remoteLocation).getString(ProtocolConstants.KEY_ID);
		// nothing new
		assertEquals(refId, newRefId);
	}

	@Test
	@Ignore("will fail as long as bug 339354 is open")
	public void testPushAndFetch() throws IOException, SAXException, JSONException, URISyntaxException, JGitInternalException, GitAPIException {
		URI workspaceLocation = createWorkspace(getMethodName());

		// clone1: create
		URIish uri = new URIish(gitDir.toURL());
		String name = null;
		WebRequest request = GitCloneTest.getPostGitCloneRequest(uri, name);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		String location = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(location);
		JSONObject clone1 = new JSONObject(response.getText());
		String contentLocation1 = clone1.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(contentLocation1);

		// clone1: link
		ServletTestingSupport.allowedPrefixes = contentLocation1;
		String projectName1 = getMethodName() + "1";
		JSONObject body = new JSONObject();
		body.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentLocation1);
		InputStream in = new StringBufferInputStream(body.toString());
		// http://<host>/workspace/<workspaceId>/
		request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName1 != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName1);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project1 = new JSONObject(response.getText());
		JSONObject gitSection1 = project1.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection1);
		String gitRemoteUri1 = gitSection1.optString(GitConstants.KEY_REMOTE, null);
		assertNotNull(gitRemoteUri1);

		// clone2: create
		request = GitCloneTest.getPostGitCloneRequest(uri, name);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		location = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		assertNotNull(location);
		JSONObject clone2 = new JSONObject(response.getText());
		String contentLocation2 = clone2.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(contentLocation1);

		// clone2: link
		ServletTestingSupport.allowedPrefixes = contentLocation2;
		String projectName2 = getMethodName() + "2";
		body = new JSONObject();
		body.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentLocation2);
		in = new StringBufferInputStream(body.toString());
		// http://<host>/workspace/<workspaceId>/
		request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName2 != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName2);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project2 = new JSONObject(response.getText());
		String projectId2 = project2.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId2);
		JSONObject gitSection2 = project2.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection2);
		String gitRemoteUri2 = gitSection2.optString(GitConstants.KEY_REMOTE, null);
		assertNotNull(gitRemoteUri2);
		String gitIndexUri2 = gitSection2.optString(GitConstants.KEY_INDEX, null);
		assertNotNull(gitIndexUri2);
		String gitCommitUri2 = gitSection2.optString(GitConstants.KEY_COMMIT, null);
		assertNotNull(gitCommitUri2);

		// clone1: list remotes
		request = GitRemoteTest.getGetGitRemoteRequest(gitRemoteUri1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject remotes = new JSONObject(response.getText());
		JSONArray remotesArray = remotes.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, remotesArray.length());
		JSONObject remote = remotesArray.getJSONObject(0);
		assertNotNull(remote);
		assertEquals(Constants.DEFAULT_REMOTE_NAME, remote.getString(ProtocolConstants.KEY_NAME));
		String remoteLocation1 = remote.getString(ProtocolConstants.KEY_LOCATION);
		assertNotNull(remoteLocation1);

		// clone1: get remote details
		JSONObject details = getRemoteBranch(remoteLocation1);
		String refId1 = details.getString(ProtocolConstants.KEY_ID);
		String remoteBranchLocation1 = details.getString(ProtocolConstants.KEY_LOCATION);

		// clone2: change
		request = getPutFileRequest(projectId2 + "/test.txt", "incoming change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri2);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: commit
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri2, "incoming change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: push
		// TODO: replace with REST API for git push once bug 339115 is fixed
		FileRepository db2 = new FileRepository(new File(URIUtil.toFile(new URI(contentLocation2)), Constants.DOT_GIT));
		Git git = new Git(db2);
		git.push().call();

		// clone1: fetch
		request = getPostGitRemoteRequest(remoteBranchLocation1, true);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: get remote details again
		String newRefId1 = getRemoteBranch(remoteLocation1).getString(ProtocolConstants.KEY_ID);
		// an incoming commit
		assertFalse(refId1.equals(newRefId1));

		// clone1: log master..origin/master
		// TODO replace with tests methods from GitLogTest
		FileRepository db1 = new FileRepository(new File(URIUtil.toFile(new URI(contentLocation1)), Constants.DOT_GIT));
		ObjectId master = db1.resolve(Constants.MASTER);
		ObjectId originMaster = db1.resolve(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + '/' + Constants.MASTER);
		Iterable<RevCommit> commits = git.log().addRange(master, originMaster).call();
		int c = 0;
		for (RevCommit commit : commits) {
			assertEquals("incoming change commit", commit.getFullMessage());
			c++;
		}
		// a single incoming commit
		assertEquals(1, c);
	}

	private JSONObject getRemoteBranch(String remoteLocation) throws IOException, SAXException, JSONException {
		WebRequest request = GitRemoteTest.getGetGitRemoteRequest(remoteLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject remote = new JSONObject(response.getText());
		assertNotNull(remote);
		assertEquals(Constants.DEFAULT_REMOTE_NAME, remote.getString(ProtocolConstants.KEY_NAME));
		assertNotNull(remote.getString(ProtocolConstants.KEY_LOCATION));
		JSONArray refsArray = remote.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, refsArray.length());
		JSONObject ref = refsArray.getJSONObject(0);
		assertNotNull(ref);
		assertEquals(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + Constants.MASTER, ref.getString(ProtocolConstants.KEY_NAME));
		String newRefId = ref.getString(ProtocolConstants.KEY_ID);
		assertNotNull(newRefId);
		assertTrue(ObjectId.isId(newRefId));
		String remoteBranchLocation = ref.getString(ProtocolConstants.KEY_LOCATION);
		assertNotNull(remoteBranchLocation);
		return ref;
	}

	private static WebRequest getPostGitRemoteRequest(String location, boolean fetch) throws JSONException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.COMMIT_RESOURCE + location;

		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_FETCH, Boolean.toString(fetch));
		InputStream in = new StringBufferInputStream(body.toString());
		WebRequest request = new PostMethodWebRequest(requestURI, in, "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}