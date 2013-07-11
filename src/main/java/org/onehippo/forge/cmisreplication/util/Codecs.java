/*
 * Copyright (C) 2012-2013 Hippo B.V. (http://www.onehippo.com)
 */

package org.onehippo.forge.cmisreplication.util;

import org.hippoecm.repository.api.StringCodec;
import org.hippoecm.repository.api.StringCodecFactory;

public final class Codecs {

    private static StringCodec display = new StringCodecFactory.IdentEncoding();

    private static StringCodec node = new StringCodecFactory.UriEncoding();

    private Codecs() {
    }

    public static String localizeName(String name) {
        return display.encode(name);
    }

    public static String encodeNode(String name) {
        return node.encode(name);
    }

}
