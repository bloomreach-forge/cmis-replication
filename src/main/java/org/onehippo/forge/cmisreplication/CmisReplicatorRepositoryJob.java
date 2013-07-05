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

import javax.jcr.RepositoryException;

import org.onehippo.repository.scheduling.RepositoryJob;
import org.onehippo.repository.scheduling.RepositoryJobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CmisReplicatorRepositoryJob
 */
public class CmisReplicatorRepositoryJob implements RepositoryJob {

    private static Logger log = LoggerFactory.getLogger(CmisReplicatorRepositoryJob.class);

    @Override
    public void execute(RepositoryJobExecutionContext context) throws RepositoryException {
        log.debug("Executing '{}'.", CmisReplicatorRepositoryJob.class.getName());
    }

}
