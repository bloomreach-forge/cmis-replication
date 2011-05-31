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
import org.apache.chemistry.opencmis.client.api.ObjectType;
import org.apache.chemistry.opencmis.client.api.OperationContext;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.api.SessionFactory;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.commons.lang.StringUtils;
import org.onehippo.forge.cmisreplication.util.AssetMetadata;
import org.onehippo.forge.cmisreplication.util.AssetUtils;
import org.onehippo.forge.cmisreplication.util.CmisDocumentBinary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CmisDocumentsReplicator {
    
    private static Logger log = LoggerFactory.getLogger(CmisDocumentsReplicator.class);
    
    private CmisRepoConfig cmisRepoConfig;
    private HippoRepoConfig hippoRepoConfig;
    private boolean updateCmisDocumentsToRepository;
    private boolean updateRepositoryDocumentsToCmis;

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
    
    public boolean isUpdateCmisDocumentsToRepository() {
        return updateCmisDocumentsToRepository;
    }

    public void setUpdateCmisDocumentsToRepository(boolean updateCmisDocumentsToRepository) {
        this.updateCmisDocumentsToRepository = updateCmisDocumentsToRepository;
    }

    public boolean isUpdateRepositoryDocumentsToCmis() {
        return updateRepositoryDocumentsToCmis;
    }

    public void setUpdateRepositoryDocumentsToCmis(boolean updateRepositoryDocumentsToCmis) {
        this.updateRepositoryDocumentsToCmis = updateRepositoryDocumentsToCmis;
    }

    public void execute() {
        log.debug("Executing Cmis Documents Replicator ...");
        
        if (updateCmisDocumentsToRepository) {
            try {
                updateCmisDocumentsToRepository();
            } catch (Exception e) {
                log.warn("Failed to update Cmis Documents To Repository. {}", e.toString());
            }
        }
        
        if (updateRepositoryDocumentsToCmis) {
            try {
                updateRepositoryDocumentsToCmis();
            } catch (Exception e) {
                log.warn("Failed to update Repository Documents To Cmis. {}", e.toString());
            }
        }
    }
    
    private void updateCmisDocumentsToRepository() throws RepositoryException, IOException {
        Session session = null;
        javax.jcr.Session jcrSession = null;
        
        try {
            jcrSession = hippoRepoConfig.getRepository().login(hippoRepoConfig.getDefaultCredentials());
            
            session = createSession();
            OperationContext operationContext = session.createOperationContext();
            operationContext.setMaxItemsPerPage(cmisRepoConfig.getMaxItemsPerPage());
            
            CmisObject seed = null;
            
            if (StringUtils.isBlank(cmisRepoConfig.getRootPath())) {
                seed = session.getRootFolder(operationContext);
            } else {
                seed = session.getObjectByPath(cmisRepoConfig.getRootPath(), operationContext);
            }
            
            List<String> documentIds = new LinkedList<String>();
            fillAllDocumentIdsFromCMIS(seed, documentIds);
            
            for (String documentId : documentIds) {
                Document document = null;
                
                try {
                    document = (Document) session.getObject(documentId);
                } catch (CmisObjectNotFoundException ignore) {
                }
                
                if (document == null) {
                    continue;
                }
                
                //log.debug("Processing document: " + document.getPaths());
                String assetPath = 
                    hippoRepoConfig.getRootPath() + "/" + 
                    StringUtils.removeStart(StringUtils.removeStart(document.getPaths().get(0), cmisRepoConfig.getRootPath()), "/");
                AssetMetadata metadata = AssetUtils.getAssetMetadata(jcrSession, assetPath);
                long documentLastModified = document.getLastModificationDate().getTimeInMillis();
                
                if (metadata == null || documentLastModified > metadata.getLastModified()) {
                    int offset = assetPath.lastIndexOf("/");
                    String assetFolderPath = assetPath.substring(0, offset);
                    String assetName = assetPath.substring(offset + 1);
                    Node assetFolderNode = AssetUtils.createAssetFolders(jcrSession, assetFolderPath);
                    CmisDocumentBinary binary = new CmisDocumentBinary(document);
                    AssetUtils.updateAsset(jcrSession, assetFolderNode, assetName, document, binary);
                    log.debug("Updated asset on {}", assetPath);
                }
            }
        } finally {
            if (session != null) {
                try {
                    session.clear();
                } catch (Exception ignore) {
                }
            }
            if (jcrSession != null) {
                try {
                    jcrSession.logout();
                } catch (Exception ignore) {
                }
            }
        }
    }
    
    private void updateRepositoryDocumentsToCmis() throws RepositoryException, IOException {
        Session session = null;
        javax.jcr.Session jcrSession = null;
        
        try {
            jcrSession = hippoRepoConfig.getRepository().login(hippoRepoConfig.getDefaultCredentials());
            
            session = createSession();
            OperationContext operationContext = session.createOperationContext();
            operationContext.setMaxItemsPerPage(cmisRepoConfig.getMaxItemsPerPage());
            
            Node seed = null;
            
            if (jcrSession.itemExists(hippoRepoConfig.getRootPath())) {
                seed = jcrSession.getNode(hippoRepoConfig.getRootPath());
            }
            
            if (seed != null) {
                List<String> documentIds = new LinkedList<String>();
                fillAllDocumentIdsFromRepository(seed, documentIds);
                
                for (String documentId : documentIds) {
                    Document document = null;
                    
                    try {
                        document = (Document) session.getObject(documentId);
                    } catch (CmisObjectNotFoundException ignore) {
                    }
                    
                    if (document == null) {
                        try {
                            removeAssetByDocumentId(jcrSession, documentId);
                            log.debug("Removed asset node corresponding to " + documentId);
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
            if (jcrSession != null) {
                try {
                    jcrSession.logout();
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
        Session session = factory.createSession(sessionParams);
        
        return session;
    }
    
    private void fillAllDocumentIdsFromCMIS(CmisObject seed, List<String> documentIds) {
        String baseType = seed.getBaseType().getId();
        
        if (ObjectType.DOCUMENT_BASETYPE_ID.equals(baseType)) {
            documentIds.add(seed.getId());
        } else if (ObjectType.FOLDER_BASETYPE_ID.equals(baseType)) {
            ItemIterable<CmisObject> children = ((Folder) seed).getChildren();
            
            if (children.getTotalNumItems() > 0) {
                for (CmisObject item : children) {
                    fillAllDocumentIdsFromCMIS(item, documentIds);
                }
            }
        }
    }
    
    private void fillAllDocumentIdsFromRepository(Node seed, List<String> documentIds) throws RepositoryException {
        if (seed.isNodeType(CmisReplicationTypes.HIPPO_HANDLE)) {
            seed = seed.getNode(seed.getName());
        }
        
        if (seed.isNodeType(CmisReplicationTypes.EXAMPLE_ASSET_SET) && seed.hasProperty(CmisReplicationTypes.CMIS_OBJECT_ID)) {
            documentIds.add(seed.getProperty(CmisReplicationTypes.CMIS_OBJECT_ID).getString());
        } else if (seed.isNodeType(CmisReplicationTypes.HIPPO_ASSET_GALLERY)) {
            for (NodeIterator nodeIt = seed.getNodes(); nodeIt.hasNext(); ) {
                Node child = nodeIt.nextNode();
                
                if (child != null) {
                    fillAllDocumentIdsFromRepository(child, documentIds);
                }
            }
        }
    }
    
    private void removeAssetByDocumentId(javax.jcr.Session jcrSession, String documentId) throws RepositoryException {
        QueryManager queryManager = jcrSession.getWorkspace().getQueryManager();
        String statement = "//element(*," + CmisReplicationTypes.EXAMPLE_ASSET_SET + ")[@" + CmisReplicationTypes.CMIS_OBJECT_ID +"='" + documentId + "']";
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
