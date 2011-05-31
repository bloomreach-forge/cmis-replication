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
import java.io.InputStream;
import java.io.Serializable;

import javax.jcr.Binary;
import javax.jcr.RepositoryException;

import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.commons.io.IOUtils;

public class CmisDocumentBinary implements Binary, Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private Document document;
    private ContentStream contentStream;
    private InputStream inputStream;
    
    public CmisDocumentBinary(Document document) {
        this.document = document;
    }
    
    public void dispose() {
        if (inputStream != null) {
            IOUtils.closeQuietly(inputStream);
            inputStream = null;
        }
        
        contentStream = null;
    }
    
    public long getSize() throws RepositoryException {
        if (document != null) {
            return document.getContentStreamLength();
        }
        
        return 0;
    }
    
    public InputStream getStream() throws RepositoryException {
        if (inputStream == null) {
            contentStream = document.getContentStream();
            inputStream = contentStream.getStream();
        }
        
        return inputStream;
    }
    
    public int read(byte[] b, long position) throws IOException, RepositoryException {
        return inputStream.read(b, (int) position, b.length);
    }
    
}
