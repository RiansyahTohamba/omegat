package org.omegat.core.team2.impl;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.omegat.core.team2.IRemoteRepository2;
import org.omegat.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class GitCommit {

    public String commitUpload(String comment) throws IRemoteRepository2.NetworkException, GitAPIException {
        Log.logInfoRB("GIT_START", "upload");

        try (Git git = new Git(repository)) {
            RevCommit commit = git.commit().setMessage(comment).call();
            Iterable<PushResult> results = git.push().setTimeout(TIMEOUT).setRemote(REMOTE).add(LOCAL_BRANCH).call();
            List<RemoteRefUpdate.Status> statuses = StreamSupport.stream(results.spliterator(), false).flatMap(r -> r.getRemoteUpdates().stream()).map(RemoteRefUpdate::getStatus).collect(Collectors.toList());
            String result;
            if (statuses.isEmpty() || statuses.stream().anyMatch(s -> s != RemoteRefUpdate.Status.OK)) {
                Log.logWarningRB("GIT_CONFLICT");
                result = null;
            } else {
                result = commit.getName();
            }
            Log.logDebug(LOGGER, "GIT committed into new version {0} ", result);
            Log.logInfoRB("GIT_FINISH", "upload");
            return result;
        } catch (Exception ex) {
            Log.logErrorRB("GIT_ERROR", "upload", ex.getMessage());
            if (ex instanceof TransportException) {
                throw new IRemoteRepository2.NetworkException(ex);
            } else {
                throw ex;
            }
        }
    }

    public void commitVersion(String[] onVersions) throws IOException {
        if (onVersions != null) {
            // check versions
            String currentVersion = new GITRemoteRepository2().getCurrentVersion();
            boolean hasVersion = false;
            for (String v : onVersions) {
                if (v != null) {
                    hasVersion = true;
                    break;
                }
            }
            if (hasVersion) {
                boolean found = false;
                for (String v : onVersions) {
                    if (v != null) {
                        if (v.equals(currentVersion)) {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    throw new RuntimeException(
                            "Version changed from " + Arrays.toString(onVersions) + " to " + currentVersion);
                }
            }
        }
    }

}
