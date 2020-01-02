package org.omegat.core.data;public class ProjectTokenizer{private final org.omegat.core.data.RealProject realProject;	public ProjectTokenizer(org.omegat.core.data.RealProject realProject)	{		this.realProject = realProject;	}/**
     * {@inheritDoc}
     */
    public org.omegat.tokenizer.ITokenizer getSourceTokenizer() {
        return realProject.getSourceTokenizer();
    }/**
     * {@inheritDoc}
     */
    public org.omegat.tokenizer.ITokenizer getTargetTokenizer() {
        return realProject.getTargetTokenizer();
    }}