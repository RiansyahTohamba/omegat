package org.omegat.core.team2.impl;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.omegat.core.team2.IRemoteRepository2;
import org.omegat.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class GitOperation {
    private static final Logger LOGGER = Logger.getLogger(GITRemoteRepository2.class.getName());
    protected static final String LOCAL_BRANCH = "master";
    protected static final String REMOTE_BRANCH = "origin/master";
    protected static final String REMOTE = "origin";
    protected static final int TIMEOUT = 30; // seconds

    public boolean indexIsEmpty(DirCache dc) throws Exception {

        DirCacheIterator dci = new DirCacheIterator(dc);
        AbstractTreeIterator old = prepareTreeParser(repository, repository.resolve(Constants.HEAD));
        try (Git git = new Git(repository)) {
            List<DiffEntry> diffs = git.diff().setOldTree(old).setNewTree(dci).call();
            return diffs.isEmpty();
        }
    }
    private static AbstractTreeIterator prepareTreeParser(Repository repository, ObjectId objId) throws Exception {
        // from the commit we can build the tree which allows us to construct
        // the TreeParser
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(objId);
            RevTree tree = walk.parseTree(commit.getTree().getId());
            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            ObjectReader reader = repository.newObjectReader();
            treeParser.reset(reader, tree.getId());
            return treeParser;
        }
    }
    public void gitSwitchVersion(String version, Git git) throws GitAPIException {
        if (version == null) {
            version = REMOTE_BRANCH;
            // TODO fetch
            git.fetch().setRemote(REMOTE).setTimeout(TIMEOUT).call();
        }
        Log.logDebug(LOGGER, "GIT switchToVersion {0} ", version);
        git.reset().setMode(ResetCommand.ResetType.HARD).call();
        git.checkout().setName(version).call();
        git.branchDelete().setForce(true).setBranchNames(LOCAL_BRANCH).call();
        git.checkout().setCreateBranch(true).setName(LOCAL_BRANCH).setStartPoint(version).call();
    }

    public String getCurrentVersion() throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            Ref localBranch = repository.findRef("HEAD");
            RevCommit headCommit = walk.lookupCommit(localBranch.getObjectId());
            return headCommit.getName();
        }
    }

    public String commitUpload(String comment) throws IRemoteRepository2.NetworkException, GitAPIException {
        Log.logInfoRB("GIT_START", "upload");

        try (Git git = new Git(repository)) {
            RevCommit commit = git.commit().setMessage(comment).call();
            Iterable<PushResult> results = git.push().setTimeout(TIMEOUT).setRemote(REMOTE).add(LOCAL_BRANCH).call();
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
            String currentVersion = getCurrentVersion();
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
