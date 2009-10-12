/*
 * Copyright 2009 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.repo.service;

import org.artifactory.api.repo.RepoPath;

/**
 * Repo path mover configuration object
 *
 * @author Noam Y. Tenne
 */
public class MoverConfig {
    private final RepoPath fromRepoPath;
    private final String targetLocalRepoKey;
    private final boolean copy;
    private final boolean dryRun;
    private final boolean executeMavenMetadataCalculation;
    private final boolean searchResult;

    /**
     * Main constructor
     *
     * @param fromRepoPath       Source repo path
     * @param targetLocalRepoKey Target repo key
     * @param copy               Is a copy being performed
     * @param dryRun             Is the current run a dry one (no items actually moved)
     * @param executeMavenMetadataCalculation
     *                           Should immediatly execute metadata calculation, or schedule
     * @param searchResult       Is moving search results
     */
    public MoverConfig(RepoPath fromRepoPath, String targetLocalRepoKey, boolean copy, boolean dryRun,
            boolean executeMavenMetadataCalculation, boolean searchResult) {
        this.fromRepoPath = fromRepoPath;
        this.targetLocalRepoKey = targetLocalRepoKey;
        this.copy = copy;
        this.dryRun = dryRun;
        this.executeMavenMetadataCalculation = executeMavenMetadataCalculation;
        this.searchResult = searchResult;
    }

    /**
     * Returns the repo path of the source
     *
     * @return Source repo path
     */
    public RepoPath getFromRepoPath() {
        return fromRepoPath;
    }

    /**
     * Returns the repo key of the target
     *
     * @return Target repo key
     */
    public String getTargetLocalRepoKey() {
        return targetLocalRepoKey;
    }

    /**
     * Indicates if a copy is being performed
     *
     * @return True if performing a copy, false if not
     */
    public boolean isCopy() {
        return copy;
    }

    /**
     * Indicates if the current run is a dry one (no items actually moved)
     *
     * @return True if run is dry, false if not
     */
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * Indicates if the metadata should be calculated immediatly or scheduled
     *
     * @return True if metadata should be calculated immediatly, false if not
     */
    public boolean isExecuteMavenMetadataCalculation() {
        return executeMavenMetadataCalculation;
    }

    /**
     * Indicates if search results are being moved (will perform empty dir cleanup)
     *
     * @return True if searct results are being moved, false if not
     */
    public boolean isSearchResult() {
        return searchResult;
    }
}