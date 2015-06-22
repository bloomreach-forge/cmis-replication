/*
 *  Copyright 2011 Hippo.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.onehippo.forge.cmisreplication;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.OperationContext;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.api.SessionFactory;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.commons.lang.StringUtils;
import org.onehippo.forge.cmisreplication.util.AssetMetadata;
import org.onehippo.forge.cmisreplication.util.AssetUtils;
import org.onehippo.forge.cmisreplication.util.CmisDocumentBinary;
import org.onehippo.forge.cmisreplication.util.Codecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CmisDocumentsReplicator {

    private static Logger log = LoggerFactory.getLogger(CmisDocumentsReplicator.class);

    private javax.jcr.Session jcrSession;
    private CmisRepoConfig cmisRepoConfig;
    private HippoRepoConfig hippoRepoConfig;
    private boolean migrateCMISDocumentsToHippo;
    private boolean deleteHippoDocumentsWhenCMISDocumentsRemoved;

    public void setJcrSession(javax.jcr.Session jcrSession) {
        this.jcrSession = jcrSession;
    }

    public CmisRepoConfig getCmisRepoConfig() {
        return cmisRepoConfig;
    }

    public void setCmisRepoConfig(CmisRepoConfig cmisRepoConfig) {
        this.cmisRepoConfig = cmisRepoConfig;
    }

    public HippoRepoConfig getHippoRepoConfig() {
        return hippoRepoConfig;
    }

    public void setHippoRepoConfig(HippoRepoConfig hippoRepoConfig) {
        this.hippoRepoConfig = hippoRepoConfig;
    }

    public boolean isMigrateCMISDocumentsToHippo() {
        return migrateCMISDocumentsToHippo;
    }

    public void setMigrateCMISDocumentsToHippo(boolean migrateCMISDocumentsToHippo) {
        this.migrateCMISDocumentsToHippo = migrateCMISDocumentsToHippo;
    }

    public boolean isDeleteHippoDocumentsWhenCMISDocumentsRemoved() {
        return deleteHippoDocumentsWhenCMISDocumentsRemoved;
    }

    public void setDeleteHippoDocumentsWhenCMISDocumentsRemoved(boolean deleteHippoDocumentsWhenCMISDocumentsRemoved) {
        this.deleteHippoDocumentsWhenCMISDocumentsRemoved = deleteHippoDocumentsWhenCMISDocumentsRemoved;
    }

    public void execute() {
        log.info("Starting Cmis Documents Replicator Execution ...");

        if (migrateCMISDocumentsToHippo) {
            try {
                updateCmisDocumentsToHippoRepository();
            } catch (Exception e) {
                log.warn("Failed to update Cmis Documents To Hippo Repository: " + e, e);
            }
        }

        if (deleteHippoDocumentsWhenCMISDocumentsRemoved) {
            try {
                deleteAssetsNotHavingCorrespondingCMISDocuments();
            } catch (Exception e) {
                log.warn("Failed to update Repository Documents To CMIS Repository: " + e, e);
            }
        }

        log.info("Stopping Cmis Documents Replicator Execution ...");
    }

    private void updateCmisDocumentsToHippoRepository() throws RepositoryException, IOException {
        Session session = null;

        try {
            session = createSession();
            OperationContext operationContext = session.createOperationContext();
            operationContext.setMaxItemsPerPage(cmisRepoConfig.getMaxItemsPerPage());

            CmisObject seed;

            if (StringUtils.isBlank(cmisRepoConfig.getRootPath())) {
                seed = session.getRootFolder(operationContext);
            } else {
                seed = session.getObjectByPath(cmisRepoConfig.getRootPath(), operationContext);
            }

            List<String> documentIds = new LinkedList<String>();
            fillAllDocumentIdsFromCMISRepository(seed, documentIds);
            log.debug("Found {} numnber of documents", documentIds.size());
            for (String documentId : documentIds) {
                Document document = null;

                try {
                    document = (Document) session.getObject(documentId);
                } catch (CmisObjectNotFoundException ignore) {
                }

                if (document == null) {
                    continue;
                }

                String remoteDocument = StringUtils.removeStart(StringUtils.substringBeforeLast(document.getPaths().get(0), "/"), cmisRepoConfig.getRootPath());

                // Remove the first start
                remoteDocument = StringUtils.removeStart(remoteDocument, "/");

                // Encode the name of the document
                String encodedAssetName = Codecs.encodeNode(document.getName());

                String assetFolderPath = hippoRepoConfig.getRootPath();
                String assetPath;

                // No folder
                if (StringUtils.isEmpty(remoteDocument)) {
                    assetPath = assetFolderPath + "/" + encodedAssetName;
                } else {
                    assetPath = assetFolderPath + "/" + remoteDocument + "/" + encodedAssetName;
                }

                AssetMetadata metadata = AssetUtils.getAssetMetadata(jcrSession, assetPath);
                long documentLastModified = document.getLastModificationDate().getTimeInMillis();

                if (metadata == null || documentLastModified > metadata.getLastModified()) {
                    Node assetFolderNode = AssetUtils.createAssetFolders(jcrSession, assetFolderPath);
                    CmisDocumentBinary binary = new CmisDocumentBinary(document);
                    AssetUtils.updateAsset(jcrSession, assetFolderNode, encodedAssetName, document, binary,
                            cmisRepoConfig.getMetadataIdsToSync());

                    binary.dispose();
                    log.info("Updated asset on {}", assetPath);
                }
            }
        } finally {
            if (session != null) {
                try {
                    session.clear();
                } catch (Exception ignore) {
                }
            }
        }
    }

    private void deleteAssetsNotHavingCorrespondingCMISDocuments() throws RepositoryException, IOException {
        Session session = null;

        try {
            session = createSession();
            OperationContext operationContext = session.createOperationContext();
            operationContext.setMaxItemsPerPage(cmisRepoConfig.getMaxItemsPerPage());

            Node seed = null;

            if (jcrSession.itemExists(hippoRepoConfig.getRootPath())) {
                seed = jcrSession.getNode(hippoRepoConfig.getRootPath());
            }

            if (seed != null) {
                List<String> documentIds = new LinkedList<String>();
                fillAllDocumentIdsFromHippoRepository(seed, documentIds);

                for (String documentId : documentIds) {
                    Document document = null;

                    // If a CMIS document is not found by the document ID stored in a Hippo asset node,
                    // then remove Hippo asset node because it is supposed to be removed from the source CMIS repository.

                    try {
                        document = (Document) session.getObject(documentId);
                    } catch (CmisObjectNotFoundException ignore) {
                    }

                    if (document == null) {
                        try {
                            removeAssetByDocumentId(jcrSession, documentId);
                            log.info("Removed asset node corresponding to " + documentId);
                        } catch (RepositoryException re) {
                            log.warn("Failed to remove node corresponding to " + documentId);
                        }
                    }
                }
            }
        } finally {
            if (session != null) {
                try {
                    session.clear();
                } catch (Exception ignore) {
                }
            }
        }
    }

    private Session createSession() {
        Map<String, String> sessionParams = new HashMap<String, String>();

        sessionParams.put(SessionParameter.USER, cmisRepoConfig.getUsername());
        sessionParams.put(SessionParameter.PASSWORD, cmisRepoConfig.getPassword());
        sessionParams.put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());
        sessionParams.put(SessionParameter.ATOMPUB_URL, cmisRepoConfig.getUrl());
        sessionParams.put(SessionParameter.REPOSITORY_ID, cmisRepoConfig.getRepositoryId());

        SessionFactory factory = SessionFactoryImpl.newInstance();

        return factory.createSession(sessionParams);
    }

    private void fillAllDocumentIdsFromCMISRepository(CmisObject seed, List<String> documentIds) {
        String baseType = seed.getBaseType().getId();
        if (BaseTypeId.CMIS_DOCUMENT.value().equals(baseType)) {
            documentIds.add(seed.getId());
        } else if (BaseTypeId.CMIS_FOLDER.value().equals(baseType)) {
            ItemIterable<CmisObject> children = ((Folder) seed).getChildren();

            if (children.getTotalNumItems() > 0) {
                for (CmisObject item : children) {
                    fillAllDocumentIdsFromCMISRepository(item, documentIds);
                }
            }
        }
    }

    private void fillAllDocumentIdsFromHippoRepository(Node seed, List<String> documentIds) throws RepositoryException {
        if (seed.isNodeType(CmisReplicationTypes.HIPPO_HANDLE)) {
            if (seed.hasNode(seed.getName())) {
                seed = seed.getNode(seed.getName());
            }
        }

        if (seed.isNodeType(CmisReplicationTypes.CMIS_DOCUMENT_TYPE) && seed.hasProperty(CmisReplicationTypes.CMIS_OBJECT_ID)) {
            documentIds.add(seed.getProperty(CmisReplicationTypes.CMIS_OBJECT_ID).getString());
        } else if (seed.isNodeType(CmisReplicationTypes.HIPPO_ASSET_GALLERY)) {
            for (NodeIterator nodeIt = seed.getNodes(); nodeIt.hasNext(); ) {
                Node child = nodeIt.nextNode();

                if (child != null) {
                    fillAllDocumentIdsFromHippoRepository(child, documentIds);
                }
            }
        }
    }

    private void removeAssetByDocumentId(javax.jcr.Session jcrSession, String documentId) throws RepositoryException {
        QueryManager queryManager = jcrSession.getWorkspace().getQueryManager();
        String statement = "//element(*," + CmisReplicationTypes.CMIS_DOCUMENT_TYPE + ")[@" + CmisReplicationTypes.CMIS_OBJECT_ID + "='" + documentId + "']";
        Query query = queryManager.createQuery(statement, Query.XPATH);
        QueryResult result = query.execute();
        boolean removed = false;

        for (NodeIterator nodeIt = result.getNodes(); nodeIt.hasNext(); ) {
            Node node = nodeIt.nextNode();

            if (node != null) {
                Node parentNode = node.getParent();

                if (parentNode.isNodeType(CmisReplicationTypes.HIPPO_HANDLE)) {
                    parentNode.remove();
                    removed = true;
                }
            }
        }

        if (removed) {
            jcrSession.save();
        }
    }
}
