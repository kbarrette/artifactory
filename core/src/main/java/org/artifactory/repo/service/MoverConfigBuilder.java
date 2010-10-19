/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2010 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.artifactory.repo.service;

import org.artifactory.api.util.Builder;
import org.artifactory.repo.RepoPath;

/**
 * Repo path mover configuration object builder
 *
 * @author Noam Y. Tenne
 */
public class MoverConfigBuilder implements Builder<MoverConfig> {
    private RepoPath fromRepoPath;
    private String targetLocalRepoKey;
    private RepoPath targetLocalRepoPath;
    private boolean copy = false;
    private boolean dryRun = false;
    private boolean executeMavenMetadataCalculation = false;
    private boolean searchResult = false;

    /**
     * @param fromRepoPath       Source repo path
     * @param targetLocalRepoKey Target repo key
     */
    public MoverConfigBuilder(RepoPath fromRepoPath, String targetLocalRepoKey) {
        this.fromRepoPath = fromRepoPath;
        this.targetLocalRepoKey = targetLocalRepoKey;
    }

    public MoverConfigBuilder(RepoPath fromRepoPath, RepoPath targetLocalRepoPath) {
        this.fromRepoPath = fromRepoPath;
        this.targetLocalRepoPath = targetLocalRepoPath;
    }

    /**
     * Indicate if a copy is being performed
     *
     * @param copy True if performing a copy, false if not
     * @return MoverConfigBuilder
     */
    public MoverConfigBuilder copy(boolean copy) {
        this.copy = copy;
        return this;
    }

    /**
     * Indicate if the current run is a dry one (no items actually moved)
     *
     * @param run True if run is dry, false if not
     * @return MoverConfigBuilder
     */
    public MoverConfigBuilder dryRun(boolean run) {
        this.dryRun = run;
        return this;
    }

    /**
     * Indicate if the metadata should be calculated immediatly or scheduled
     *
     * @param calculation True if metadata should be calculated immediatly, false if not
     * @return MoverConfigBuilder
     */
    public MoverConfigBuilder executeMavenMetadataCalculation(boolean calculation) {
        this.executeMavenMetadataCalculation = calculation;
        return this;
    }

    /**
     * Indicate if search results are being moved (will perform empty dir cleanup)
     *
     * @param searchResult True if searct results are being moved, false if not
     * @return MoverConfigBuilder
     */
    public MoverConfigBuilder searchResult(boolean searchResult) {
        this.searchResult = searchResult;
        return this;
    }

    /**
     * Builds a mover configuration object with the given parameters
     *
     * @return MoverConfig
     */
    public MoverConfig build() {
        return new MoverConfig(fromRepoPath, targetLocalRepoPath, targetLocalRepoKey,
                copy, dryRun, executeMavenMetadataCalculation, searchResult);
    }
}