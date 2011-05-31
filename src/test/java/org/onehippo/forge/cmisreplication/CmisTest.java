/*
 *  Copyright 2008 Hippo.
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.ObjectType;
import org.apache.chemistry.opencmis.client.api.OperationContext;
import org.apache.chemistry.opencmis.client.api.Property;
import org.apache.chemistry.opencmis.client.api.QueryResult;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.api.SessionFactory;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.enums.PropertyType;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

// If you run a nuxeo server somewhere accessible via http://localhost:8080/nuxeo/atom/cmis,
// then this test will work.
@Ignore
public class CmisTest {
    
    private Session session;
    private int maxItemsPerPage = 100;
    private int skipCount = 0;
    private OperationContext operationContext;
    
    @Before
    public void setUp() throws Exception {
        Map<String, String> sessionParams = new HashMap<String, String>();
        sessionParams.put(SessionParameter.USER, "Administrator");
        sessionParams.put(SessionParameter.PASSWORD, "Administrator");
        sessionParams.put(SessionParameter.ATOMPUB_URL, "http://localhost:8080/nuxeo/atom/cmis");
        sessionParams.put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());
        sessionParams.put(SessionParameter.REPOSITORY_ID, "default");
        
        SessionFactory factory = SessionFactoryImpl.newInstance();
        session = factory.createSession(sessionParams);
        operationContext = session.createOperationContext();
        operationContext.setMaxItemsPerPage(maxItemsPerPage);
    }
    
    @Test
    public void testListFolders() throws Exception {
        printFolder(session.getRootFolder(operationContext), operationContext, 0);
    }
    
    @Test
    public void testReadContents() throws Exception {
        List<CmisObject> childItems = new ArrayList<CmisObject>();
        fillAllChildItems(session.getRootFolder(), childItems);
        
        for (CmisObject item : childItems) {
            if (ObjectType.DOCUMENT_BASETYPE_ID.equals(item.getBaseType().getId())) {
                Document doc = (Document) item;
                printDocument(doc);
                File file = new File("target/" + doc.getName());
                saveDocument(doc, file);
                System.out.println("stored file: " + file);
            }
        }
    }
    
    @Test
    public void testReadContentsByPath() throws Exception {
        printFolder((Folder) session.getObjectByPath("/Default domain/Workspaces", operationContext), operationContext, 0);
    }
    
    @Test
    public void testQuery() throws Exception {
        System.out.println("$$$$$ testQuery...");
        Folder folder = (Folder) session.getObjectByPath("/Default domain/Workspaces", operationContext);
        String queryString = "SELECT cmis:objectId FROM cmis:document WHERE in_tree('" + folder.getId() + "')";

        // execute query
        ItemIterable<QueryResult> results = session.query(queryString, false);

        for (QueryResult qResult : results) {
            String objectId = qResult.getPropertyValueByQueryName("cmis:objectId");
            Document doc = (Document) session.getObject(session.createObjectId(objectId));
            printDocument(doc);
        }
    }
    
    private void printFolder(Folder folder, OperationContext operationContext, int level) {
        for (int i = 0; i < level; i++) {
            System.out.print("  ");
        }
        System.out.println("+ " + folder.getName() + " (" + folder.getBaseType().getId() + ") {" + folder.getId() + "}");
        
        ItemIterable<CmisObject> children = folder.getChildren(operationContext);
        
        if (children.getTotalNumItems() > 0) {
            for (CmisObject item : children.skipTo(skipCount).getPage()) {
                if (ObjectType.FOLDER_BASETYPE_ID.equals(item.getBaseType().getId())) {
                    printFolder((Folder) item, operationContext, level + 1);
                } else {
                    for (int i = 0; i < level + 1; i++) {
                        System.out.print("  ");
                    }
                    System.out.println("- " + item.getName() + " (" + item.getBaseType().getId() + ") {" + item.getId() + "}");                    
                }
            }
        }
    }
    
    private void fillAllChildItems(CmisObject seed, List<CmisObject> childItems) {
        childItems.add(seed);
        
        if (ObjectType.FOLDER_BASETYPE_ID.equals(seed.getBaseType().getId())) {
            ItemIterable<CmisObject> children = ((Folder) seed).getChildren();
            
            if (children.getTotalNumItems() > 0) {
                for (CmisObject item : children) {
                    fillAllChildItems(item, childItems);
                }
            }
        }
    }
    
    private void printDocument(Document document) {
        System.out.println("[Document: " + document.getName() + "]");
        
        for (Property<?> p : document.getProperties()) {
            if (PropertyType.DATETIME == p.getType()) {
                Calendar calValue = (Calendar) p.getValue();
                System.out.println("  - " + p.getId() + "=" + (calValue != null ? DateFormatUtils.format(calValue, "yyyy-MM-dd HH:mm:ss z") : ""));
            } else {
                System.out.println("  - " + p.getId() + "=" + p.getValue());
            }
        }
    }
    
    private void saveDocument(Document document, File file) throws IOException {
        InputStream input = null;
        OutputStream output = null;
        
        try {
            input = document.getContentStream().getStream();
            output = new FileOutputStream(file);
            IOUtils.copy(input, output);
        } finally {
            if (output != null) {
                IOUtils.closeQuietly(output);
            }
            if (input != null) {
                IOUtils.closeQuietly(input);
            }
        }
    }
}
