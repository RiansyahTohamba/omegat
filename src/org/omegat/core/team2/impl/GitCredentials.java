package org.omegat.core.team2.impl;public class GitCredentials{private final org.omegat.core.team2.impl.GITRemoteRepository2 GITRemoteRepository2;	public GitCredentials(org.omegat.core.team2.impl.GITRemoteRepository2 GITRemoteRepository2)	{		this.GITRemoteRepository2 = GITRemoteRepository2;	}public void setCredentials(gen.core.project.RepositoryDefinition repo,org.omegat.core.team2.ProjectTeamSettings teamSettings) {
        java.lang.String predefinedUser = repo.getOtherAttributes().get(new javax.xml.namespace.QName("gitUsername"));
        java.lang.String predefinedPass = repo.getOtherAttributes().get(new javax.xml.namespace.QName("gitPassword"));
        java.lang.String predefinedFingerprint = repo.getOtherAttributes().get(new javax.xml.namespace.QName("gitFingerprint"));
        
        org.omegat.core.team2.impl.GITCredentialsProvider gcp = (org.omegat.core.team2.impl.GITCredentialsProvider) org.eclipse.jgit.transport.CredentialsProvider.getDefault();
        gcp.setTeamSettings(teamSettings);
        gcp.setPredefinedCredentials(GITRemoteRepository2.getRepositoryURL(),predefinedUser, predefinedPass, predefinedFingerprint);
    }}