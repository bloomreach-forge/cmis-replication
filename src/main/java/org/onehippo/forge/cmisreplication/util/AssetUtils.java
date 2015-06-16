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
package org.onehippo.forge.cmisreplication.util;

import java.io.IOException;
import java.util.List;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Property;
import org.apache.commons.lang.StringUtils;
import org.onehippo.forge.cmisreplication.CmisReplicationTypes;

public class AssetUtils {

    private AssetUtils() {

    }

    public static AssetMetadata getAssetMetadata(Session session, String path) throws RepositoryException {

        if (!session.itemExists(path)) {
            return null;
        }

        AssetMetadata metadata = null;

        Node node = session.getRootNode().getNode(StringUtils.removeStart(path, "/"));

        if (node.isNodeType(CmisReplicationTypes.HIPPO_HANDLE)) {
            if (node.hasNode(node.getName())) {
                node = node.getNode(node.getName());
            } else {
                return null;
            }
        }

        Node resourceChildNode = null;

        if (node.hasNode(CmisReplicationTypes.HIPPOGALLERY_ASSET)) {
            resourceChildNode = node.getNode(CmisReplicationTypes.HIPPOGALLERY_ASSET);
        }

        if (resourceChildNode != null) {
            metadata = new AssetMetadata();
            metadata.setPath(path);

            if (resourceChildNode.hasProperty("jcr:mimeType")) {
                metadata.setMimeType(resourceChildNode.getProperty("jcr:mimeType").getString());
            }

            if (resourceChildNode.hasProperty("jcr:lastModified")) {
                metadata.setLastModified(resourceChildNode.getProperty("jcr:lastModified").getLong());
            }
        }

        return metadata;
    }

    public static void updateAsset(Session session, Node folderNode, String encodedAssetName, Document document, Binary binaryData, final List<String> metadataIdsToSync) throws RepositoryException, IOException {
        Node assetHandleNode = null;

        if (folderNode.hasNode(encodedAssetName)) {
            assetHandleNode = folderNode.getNode(encodedAssetName);

            if (!assetHandleNode.isNodeType(CmisReplicationTypes.HIPPO_HANDLE) || !assetHandleNode.isNodeType(CmisReplicationTypes.HIPPO_HARD_HANDLE)) {
                assetHandleNode.remove();
                assetHandleNode = null;
            }
        }

        if (assetHandleNode == null) {
            assetHandleNode = folderNode.addNode(encodedAssetName, CmisReplicationTypes.HIPPO_HANDLE);
            assetHandleNode.addMixin(CmisReplicationTypes.MIX_REFERENCEABLE);
            assetHandleNode.addMixin(CmisReplicationTypes.HIPPO_TRANSLATED);

            // Add translation node. This node is used to manager special name
            Node translation = assetHandleNode.addNode(CmisReplicationTypes.HIPPO_TRANSLATION, CmisReplicationTypes.HIPPO_TRANSLATION);
            translation.setProperty(CmisReplicationTypes.HIPPO_LANGUAGE, "");
            translation.setProperty(CmisReplicationTypes.HIPPO_MESSAGE, document.getName());
        }

        Node assetNode;

        if (assetHandleNode.hasNode(encodedAssetName)) {
            assetNode = assetHandleNode.getNode(encodedAssetName);

            if (assetNode.isNodeType(CmisReplicationTypes.CMIS_DOCUMENT_TYPE)) {
                assetNode.addMixin(CmisReplicationTypes.CMIS_DOCUMENT_TYPE);
            }
        } else {
            assetNode = assetHandleNode.addNode(encodedAssetName, CmisReplicationTypes.HIPPO_EXAMPLE_ASSET_SET);
            assetNode.addMixin(CmisReplicationTypes.CMIS_DOCUMENT_TYPE);
            assetNode.setProperty(CmisReplicationTypes.HIPPO_AVAILABILITY, new String[]{"live", "preview"});
        }

        assetNode.setProperty(CmisReplicationTypes.CMIS_OBJECT_ID, document.getId());
        assetNode.setProperty(CmisReplicationTypes.CMIS_NAME, document.getName());
        assetNode.setProperty(CmisReplicationTypes.CMIS_CREATED_BY, document.getCreatedBy());
        assetNode.setProperty(CmisReplicationTypes.CMIS_CREATION_DATE, document.getCreationDate());
        assetNode.setProperty(CmisReplicationTypes.CMIS_LAST_MODIFIED_BY, document.getLastModifiedBy());
        assetNode.setProperty(CmisReplicationTypes.CMIS_LAST_MODIFICATION_DATE, document.getLastModificationDate());
        assetNode.setProperty(CmisReplicationTypes.CMIS_VERSION_LABEL, document.getVersionLabel());

        // Insert the metadata
        List<Property<?>> propertiesList = document.getProperties();

        for (Property<?> property : propertiesList) {

            if (metadataIdsToSync.contains(property.getId()) && property.getValues() != null) {

                List metadataValues = property.getValues();

                String[] values = (String[]) metadataValues.toArray(new String[metadataValues.size()]);
                assetNode.setProperty(StringUtils.substringAfter(property.getId(), ":"), values);
            }
        }

        Node resourceChildNode;

        if (!assetNode.hasNode(CmisReplicationTypes.HIPPOGALLERY_ASSET)) {
            resourceChildNode = assetNode.addNode(CmisReplicationTypes.HIPPOGALLERY_ASSET, CmisReplicationTypes.HIPPO_RESOURCE);
        } else {
            resourceChildNode = assetNode.getNode(CmisReplicationTypes.HIPPOGALLERY_ASSET);
        }

        resourceChildNode.setProperty("jcr:mimeType", document.getContentStreamMimeType());
        resourceChildNode.setProperty("jcr:data", binaryData);
        resourceChildNode.setProperty("jcr:lastModified", System.currentTimeMillis());

        session.save();
    }

    public static Node createAssetFolders(Session session, String assetFolderPath) throws RepositoryException {
        if (session.itemExists(assetFolderPath)) {
            return session.getRootNode().getNode(StringUtils.removeStart(assetFolderPath, "/"));
        }

        if (StringUtils.isBlank(assetFolderPath)) {
            throw new IllegalArgumentException("Blank folder path: " + assetFolderPath);
        }

        String [] folderComponents = StringUtils.split(assetFolderPath, "/");

        Node curNode = session.getRootNode();

        for (String folderComponent : folderComponents) {
            if (!StringUtils.isEmpty(folderComponent)) {
                if (curNode.hasNode(folderComponent)) {
                    curNode = curNode.getNode(folderComponent);
                } else {
                    curNode = createAssetFolder(session, curNode, folderComponent);
                }
            }
        }

        return curNode;
    }

    public static Node createAssetFolder(Session session, Node parentFolderNode, String folderName) throws RepositoryException {
        if (parentFolderNode.hasNode(folderName)) {
            return parentFolderNode.getNode(folderName);
        }

        Node folderNode = parentFolderNode.addNode(folderName, CmisReplicationTypes.HIPPO_ASSET_GALLERY);
        folderNode.addMixin(CmisReplicationTypes.MIX_REFERENCEABLE);
        folderNode.setProperty(CmisReplicationTypes.HIPPOSTD_GALLERY_TYPE, new String [] { CmisReplicationTypes.HIPPO_EXAMPLE_ASSET_SET });
        folderNode.setProperty(CmisReplicationTypes.HIPPOSTD_FOLDER_TYPE, new String [] { "new-file-folder" });

        session.save();

        return folderNode;
    }
}
