/**
 * Copyright (C) 2011 Red Hat, Inc. (jdcasey@commonjava.org)
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
package org.commonjava.aprox.ftest.core.content;

import static org.commonjava.aprox.model.core.StoreType.hosted;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class StoreAndVerifyViaDirectDownloadTest
    extends AbstractContentManagementTest
{

    @Test
    public void storeFileThenDownloadAndVerifyContentViaDirectDownload()
        throws Exception
    {
        final String content = "This is a test: " + System.nanoTime();
        final InputStream stream = new ByteArrayInputStream( content.getBytes() );

        final String path = "/path/to/foo.class";

        assertThat( client.content()
                          .exists( hosted, STORE, path ), equalTo( false ) );

        client.content()
              .store( hosted, STORE, path, stream );

        assertThat( client.content()
                          .exists( hosted, STORE, path ), equalTo( true ) );

        final URL url = new URL( client.content()
                                       .contentUrl( hosted, STORE, path ) );

        final InputStream is = url.openStream();

        final String result = IOUtils.toString( is );
        is.close();

        assertThat( result, equalTo( content ) );
    }
}