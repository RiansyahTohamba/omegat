package org.omegat.core.team2.impl;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.omegat.core.team2.IRemoteRepository2;
import org.omegat.util.Log;

import java.io.File;
import java.io.IOException;

public class GitDirectory {
    private final org.omegat.core.team2.impl.GITRemoteRepository2 GITRemoteRepository2;

    public GitDirectory(org.omegat.core.team2.impl.GITRemoteRepository2 GITRemoteRepository2) {
        this.GITRemoteRepository2 = GITRemoteRepository2;
    }

    public void checkGitDir() throws IOException, GitAPIException, IRemoteRepository2.BadRepositoryException {
        File gitDir = new File(GITRemoteRepository2.getLocalDirectory(), ".git");
        if (gitDir.exists() && gitDir.isDirectory()) {
            alreadyCloned();
        } else {
            notAlreadyCloned();
        }
    }

    public void notAlreadyCloned() throws GitAPIException, IRemoteRepository2.BadRepositoryException, IOException {
        Log.logInfoRB("GIT_START", "clone");
        CloneCommand c = Git.cloneRepository();
        c.setURI(GITRemoteRepository2.getRepositoryURL());
        c.setDirectory(GITRemoteRepository2.getLocalDirectory());
        c.setTimeout(org.omegat.core.team2.impl.GITRemoteRepository2.TIMEOUT);
        try {
            c.call();
        } catch (InvalidRemoteException e) {
            if (GITRemoteRepository2.getLocalDirectory().exists()) {
                deleteDirectory(GITRemoteRepository2.getLocalDirectory());
            }
            Throwable cause = e.getCause();
            if (cause != null && cause instanceof NoRemoteRepositoryException) {
                IRemoteRepository2.BadRepositoryException bre = new IRemoteRepository2.BadRepositoryException(
                        ((NoRemoteRepositoryException) cause).getLocalizedMessage());
                bre.initCause(e);
                throw bre;
            }
            throw e;
        }
        GITRemoteRepository2.setRepository(Git.open(GITRemoteRepository2.getLocalDirectory()).getRepository());
        try (Git git = new Git(GITRemoteRepository2.getRepository())) {
            git.submoduleInit().call();
            git.submoduleUpdate().setTimeout(org.omegat.core.team2.impl.GITRemoteRepository2.TIMEOUT).call();
        }
        GITRemoteRepository2.configRepo();
        Log.logInfoRB("GIT_FINISH", "clone");
    }

    public void alreadyCloned() throws IOException, GitAPIException {
        GITRemoteRepository2.setRepository(Git.open(GITRemoteRepository2.getLocalDirectory()).getRepository());
        GITRemoteRepository2.configRepo();
        try (Git git = new Git(GITRemoteRepository2.getRepository())) {
            git.submoduleInit().call();
            git.submoduleUpdate().setTimeout(org.omegat.core.team2.impl.GITRemoteRepository2.TIMEOUT).call();
        }
    }

    public static boolean deleteDirectory(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                } else {
                    files[i].delete();
                }
            }
        }
        return (path.delete());
    }
}