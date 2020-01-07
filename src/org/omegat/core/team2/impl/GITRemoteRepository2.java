/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2012 Alex Buloichik
               2014 Alex Buloichik, Aaron Madlon-Kay
               Home page: http://www.omegat.org/
               Support center: https://omegat.org/support

 This file is part of OmegaT.

 OmegaT is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 OmegaT is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **************************************************************************/

package org.omegat.core.team2.impl;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.omegat.core.team2.IRemoteRepository2;
import org.omegat.core.team2.ProjectTeamSettings;
import org.omegat.util.Log;

import gen.core.project.RepositoryDefinition;

/**
 * GIT repository connection implementation.
 *
 * @author Alex Buloichik (alex73mail@gmail.com)
 * @author Aaron Madlon-Kay
 */
public class GITRemoteRepository2 implements IRemoteRepository2 {

    String repositoryURL;
    File localDirectory;

    protected Repository repository;
    private GitOperation gitOperation;

    static {
        CredentialsProvider.setDefault(new GITCredentialsProvider());
    }

    @Override
    public void init(RepositoryDefinition repo, File dir, ProjectTeamSettings teamSettings) throws Exception {
        repositoryURL = repo.getUrl();
        localDirectory = dir;
        gitOperation = new GitOperation();
        new GitCredentials(this).setCredentials(repo, teamSettings);
        new GitDirectory(this).checkGitDir();
        // cleanup repository
        try (Git git = new Git(repository)) {
            git.reset().setMode(ResetType.HARD).call();
        }
    }

    @Override
    public String getFileVersion(String file) throws IOException {
        File f = new File(localDirectory, file);
        if (!f.exists()) {
            return null;
        }

        return gitOperation.getCurrentVersion();
    }


    @Override
    public void switchToVersion(String version) throws Exception {
        try (Git git = new Git(repository)) {
            gitOperation.gitSwitchVersion(version, git);
        } catch (TransportException e) {
            throw new NetworkException(e);
        }
    }

    @Override
    public void addForCommit(String path) throws Exception {
        Log.logInfoRB("GIT_START", "addForCommit");
        try (Git git = new Git(repository)) {
            git.add().addFilepattern(path).call();
            Log.logInfoRB("GIT_FINISH", "addForCommit");
        } catch (Exception ex) {
            Log.logErrorRB("GIT_ERROR", "addForCommit", ex.getMessage());
            throw ex;
        }
    }

    @Override
    public String commit(String[] onVersions, String comment) throws Exception {
        gitOperation.commitVersion(onVersions);
        if (gitOperation.indexIsEmpty(DirCache.read(repository))) {
            // Nothing was actually added to the index so we can just return.
            Log.logInfoRB("GIT_NO_CHANGES", "upload");
            return null;
        }
        return gitOperation.commitUpload(comment);
    }


    /**
     * Determines whether or not the supplied URL represents a valid Git repository.
     *
     * <p>
     * Does the equivalent of <code>git ls-remote <i>url</i></code>.
     *
     * @param url
     *            URL of supposed remote repository
     * @return true if repository appears to be valid, false otherwise
     */
    public static boolean isGitRepository(String url) {
        // Heuristics to save some waiting time
        try {
            Collection<Ref> result = new LsRemoteCommand(null).setRemote(url).setTimeout(TIMEOUT).call();
            return !result.isEmpty();
        } catch (TransportException ex) {
            String message = ex.getMessage();
            if (message.endsWith("not authorized") || message.endsWith("Auth fail")
                    || message.contains("Too many authentication failures")
                    || message.contains("Authentication is required")) {
                return true;
            }
            return false;
        } catch (GitAPIException ex) {
            return false;
        } catch (JGitInternalException ex) {
            // Happens if the URL is a Subversion URL like svn://...
            return false;
        }
    }

    public File getLocalDirectory() {
        return localDirectory;
    }

    public String getRepositoryURL() {
        return repositoryURL;
    }

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }
}
