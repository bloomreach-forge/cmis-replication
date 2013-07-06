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

public interface CmisReplicationTypes {

    public static final String HIPPO_HANDLE = "hippo:handle";
    public static final String HIPPO_HARD_HANDLE = "hippo:hardhandle";
    public static final String HIPPO_HARD_DOCUMENT = "hippo:harddocument";
    public static final String HIPPO_TRANSLATED = "hippo:translated";
    public static final String HIPPO_RESOURCE = "hippo:resource";

    public static final String HIPPOGALLERY_ASSET = "hippogallery:asset";

    public static final String HIPPO_ASSET_GALLERY = "hippogallery:stdAssetGallery";
    public static final String HIPPO_EXAMPLE_ASSET_SET = "hippogallery:exampleAssetSet";

    public static final String HIPPOSTD_GALLERY_TYPE = "hippostd:gallerytype";
    public static final String HIPPOSTD_FOLDER_TYPE = "hippostd:foldertype";

    /**
     * @deprecated Since 1.02.00, {@link CMIS_DOCUMENT_MIXIN_TYPE} is used as mixin type to the default hippo example asset node type.
     */
    public static final String EXAMPLE_ASSET_SET = "cmisreplication:exampleAssetSet";

    /**
     * Node Mixin type for nodes representing replicated CMIS document.
     */
    public static final String CMIS_DOCUMENT_TYPE = "cmisreplication:cmisdocument";

    public static final String CMIS_OBJECT_ID = "cmisreplication:objectId";
    public static final String CMIS_NAME = "cmisreplication:name";
    public static final String CMIS_CREATED_BY = "cmisreplication:createdBy";
    public static final String CMIS_CREATION_DATE = "cmisreplication:creationDate";
    public static final String CMIS_LAST_MODIFIED_BY = "cmisreplication:lastModifiedBy";
    public static final String CMIS_LAST_MODIFICATION_DATE = "cmisreplication:lastModificationDate";
    public static final String CMIS_VERSION_LABEL = "cmisreplication:versionLabel";

}