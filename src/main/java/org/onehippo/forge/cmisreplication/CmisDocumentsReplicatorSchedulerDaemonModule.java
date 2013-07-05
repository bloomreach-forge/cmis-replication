/**
 * Copyright 2013 Hippo B.V. (http://www.onehippo.com)
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
import javax.jcr.Session;

import org.hippoecm.repository.util.JcrUtils;
import org.onehippo.cms7.services.HippoServiceRegistry;
import org.onehippo.repository.modules.AbstractReconfigurableDaemonModule;
import org.onehippo.repository.modules.RequiresService;
import org.onehippo.repository.scheduling.RepositoryJobCronTrigger;
import org.onehippo.repository.scheduling.RepositoryJobInfo;
import org.onehippo.repository.scheduling.RepositoryJobTrigger;
import org.onehippo.repository.scheduling.RepositoryScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CmisDocumentsReplicatorSchedulerDaemonModule
 */
@RequiresService(types = { RepositoryScheduler.class })
public class CmisDocumentsReplicatorSchedulerDaemonModule extends AbstractReconfigurableDaemonModule {

    private static Logger log = LoggerFactory.getLogger(CmisDocumentsReplicatorSchedulerDaemonModule.class);

    private static final String JOB_INFO_NAME = CmisReplicatorRepositoryJob.class.getSimpleName() + "Info";
    private static final String JOB_TRIGGER_NAME = CmisReplicatorRepositoryJob.class.getSimpleName() + "Trigger";

    private static Session defaultSession;

    private RepositoryJobInfo cmisReplicatorRepositoryJobInfo;

    private String cronExpression;

    static Session getDefaultSession() {
        return defaultSession;
    }

    @Override
    protected void doConfigure(Node moduleConfig) throws RepositoryException {
        log.debug("Configuring daemon module, '{}'.", CmisDocumentsReplicatorSchedulerDaemonModule.class.getName());
        cronExpression = JcrUtils.getStringProperty(moduleConfig, "cronexpression", null);
    }

    @Override
    protected void doInitialize(Session session) throws RepositoryException {
        log.debug("Initializing daemon module, '{}'.", CmisDocumentsReplicatorSchedulerDaemonModule.class.getName());
        defaultSession = session;
        scheduleJob();
    }

    @Override
    protected void doShutdown() {
        log.debug("Shutting down daemon module, '{}'.", CmisDocumentsReplicatorSchedulerDaemonModule.class.getName());
        deleteScheduledJob();
    }

    private void scheduleJob() {
        log.debug("Scheduling a job in daemon module, '{}'.", CmisDocumentsReplicatorSchedulerDaemonModule.class.getName());

        try {
            final RepositoryScheduler repositoryScheduler = HippoServiceRegistry.getService(RepositoryScheduler.class);

            RepositoryJobInfo jobInfo = new RepositoryJobInfo(JOB_INFO_NAME, CmisReplicatorRepositoryJob.class);
            jobInfo.setAttribute(CmisReplicatorRepositoryJob.MODULE_CONFIG_PATH, moduleConfigPath);

            final RepositoryJobTrigger cmisReplicatorRepositoryJobTrigger = new RepositoryJobCronTrigger(JOB_TRIGGER_NAME, cronExpression);

            repositoryScheduler.scheduleJob(jobInfo, cmisReplicatorRepositoryJobTrigger);
            cmisReplicatorRepositoryJobInfo = jobInfo;
            log.info("Scheduled a job: {}", cmisReplicatorRepositoryJobInfo);
        } catch (RepositoryException e) {
            log.error("Failed to scheudle a job.", e);
        }
    }

    private void deleteScheduledJob() {
        log.debug("Deleting a job in daemon module, '{}'.", CmisDocumentsReplicatorSchedulerDaemonModule.class.getName());

        if (cmisReplicatorRepositoryJobInfo == null) {
            return;
        }

        try {
            final RepositoryScheduler repositoryScheduler = HippoServiceRegistry.getService(RepositoryScheduler.class);
            repositoryScheduler.deleteJob(cmisReplicatorRepositoryJobInfo.getName(), cmisReplicatorRepositoryJobInfo.getGroup());
            log.info("Delete job: {}", cmisReplicatorRepositoryJobInfo);
        } catch (RepositoryException e) {
            log.error("Failed to delete job: " + cmisReplicatorRepositoryJobInfo, e);
        }

        cmisReplicatorRepositoryJobInfo = null;
    }
}
