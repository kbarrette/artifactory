/*
 * This file is part of Artifactory.
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

package org.artifactory.repo.index;

import org.artifactory.api.repo.Lock;
import org.artifactory.api.repo.index.IndexerService;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.repo.RealRepo;
import org.artifactory.spring.ReloadableBean;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * @author yoavl
 */
public interface InternalIndexerService extends IndexerService, ReloadableBean {

    @Lock(transactional = true)
    void saveIndexFiles(RepoIndexerData repoIndexerData);

    @Lock(transactional = true)
    void fecthOrCreateIndex(RepoIndexerData repoIndexerData, Date fireTime);

    @Lock(transactional = true)
    void mergeVirtualReposIndexes(Set<? extends RepoDescriptor> excludedRepositories, List<RealRepo> repos);

    void index(Date fireTime, boolean manualRun);
}