/**
 * Copyright 2013-2013 Hippo B.V. (http://www.onehippo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onehippo.forge.cmisreplication;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.hippoecm.repository.util.JcrUtils;
import org.onehippo.repository.scheduling.RepositoryJob;
import org.onehippo.repository.scheduling.RepositoryJobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CmisReplicatorRepositoryJob
 */
public class CmisReplicatorRepositoryJob implements RepositoryJob {

    private static Logger log = LoggerFactory.getLogger(CmisReplicatorRepositoryJob.class);

    public static final String MODULE_CONFIG_PATH = "moduleConfigPath";
    public static final String PARAM_ACTIVE = "active";

    @Override
    public void execute(RepositoryJobExecutionContext context) throws RepositoryException {
        log.debug("Beginning '{}#execute()'.", CmisReplicatorRepositoryJob.class.getSimpleName());

        try {
            String moduleConfigPath = context.getAttribute(MODULE_CONFIG_PATH);
            Node moduleConfig = CmisDocumentsReplicatorSchedulerDaemonModule.getDefaultSession().getNode(moduleConfigPath);
            log.debug("moduleConfigNodePat: '{}'.", moduleConfig.getPath());
            executeReplication(moduleConfig);
        } catch (Exception e) {
            log.error("Failed to synchronize CMIS documents with Hippo Repository.", e);
        }

        log.debug("Ending '{}#execute()'.", CmisReplicatorRepositoryJob.class.getSimpleName());
    }

    private void executeReplication(final Node moduleConfig) throws Exception {
        CmisDocumentsReplicator replicator = new CmisDocumentsReplicator();

        final CmisRepoConfig cmisRepoConfig = createCmisRepoConfig(moduleConfig);
        final HippoRepoConfig hippoRepoConfig = createHippoRepoConfig(moduleConfig);

        replicator.setCmisRepoConfig(cmisRepoConfig);
        replicator.setHippoRepoConfig(hippoRepoConfig);
        replicator.setJcrSession(CmisDocumentsReplicatorSchedulerDaemonModule.getDefaultSession());

        replicator.setMigrateCMISDocumentsToHippo(JcrUtils.getBooleanProperty(moduleConfig, "cmis.replication.migrateCMISDocumentsToHippo", true));
        replicator.setDeleteHippoDocumentsWhenCMISDocumentsRemoved(JcrUtils.getBooleanProperty(moduleConfig, "cmis.replication.deleteHippoDocumentsWhenCMISDocumentsRemoved", true));

        replicator.execute();
    }

    private CmisRepoConfig createCmisRepoConfig(final Node moduleConfig) throws RepositoryException {
        CmisRepoConfig config = new CmisRepoConfig();

        config.setUrl(JcrUtils.getStringProperty(moduleConfig, "cmis.replication.source.url", ""));
        config.setUsername(JcrUtils.getStringProperty(moduleConfig, "cmis.replication.source.username", ""));
        config.setPassword(JcrUtils.getStringProperty(moduleConfig, "cmis.replication.source.password", ""));
        config.setRepositoryId(JcrUtils.getStringProperty(moduleConfig, "cmis.replication.source.repositoryId", ""));
        config.setRootPath(JcrUtils.getStringProperty(moduleConfig, "cmis.replication.source.rootPath", ""));
        config.setMaxItemsPerPage((int) JcrUtils.getLongProperty(moduleConfig, "cmis.replication.source.maxItemsPerPage", 500L));

        return config;
    }

    private HippoRepoConfig createHippoRepoConfig(Node moduleConfig) throws RepositoryException {
        HippoRepoConfig config = new HippoRepoConfig();

        config.setRootPath(JcrUtils.getStringProperty(moduleConfig, "cmis.replication.target.rootPath", ""));

        return config;
    }
}
