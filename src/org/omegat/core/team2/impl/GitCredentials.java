package org.omegat.core.team2.impl;

import gen.core.project.RepositoryDefinition;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.omegat.core.team2.ProjectTeamSettings;

import javax.xml.namespace.QName;

public class GitCredentials {
    private final GITRemoteRepository2 GITRemoteRepository2;

    public GitCredentials(GITRemoteRepository2 GITRemoteRepository2) {
        this.GITRemoteRepository2 = GITRemoteRepository2;
    }

    public void setCredentials(RepositoryDefinition repo, ProjectTeamSettings teamSettings) {
        String predefinedUser = repo.getOtherAttributes().get(new QName("gitUsername"));
        String predefinedPass = repo.getOtherAttributes().get(new QName("gitPassword"));
        String predefinedFingerprint = repo.getOtherAttributes().get(new QName("gitFingerprint"));

        GITCredentialsProvider gcp = (GITCredentialsProvider) CredentialsProvider.getDefault();
        gcp.setTeamSettings(teamSettings);
        gcp.setPredefinedCredentials(GITRemoteRepository2.getRepositoryURL(), predefinedUser, predefinedPass, predefinedFingerprint);
    }
}