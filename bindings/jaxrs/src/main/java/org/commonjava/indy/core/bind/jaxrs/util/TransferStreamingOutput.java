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
package org.commonjava.indy.core.bind.jaxrs.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.commonjava.maven.galley.event.EventMetadata;
import org.commonjava.maven.galley.model.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransferStreamingOutput
    implements StreamingOutput
{

    private InputStream stream;

    public TransferStreamingOutput( final InputStream stream )
    {
        this.stream = stream;
    }

    @Override
    public void write( final OutputStream out )
        throws IOException, WebApplicationException
    {
        try
        {
            CountingOutputStream cout = new CountingOutputStream( out );
            IOUtils.copy( stream, cout );

            Logger logger = LoggerFactory.getLogger( getClass() );
            logger.debug( "Wrote: {} bytes", cout.getByteCount() );
        }
        finally
        {
            IOUtils.closeQuietly( stream );
        }
    }

}
