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
package org.commonjava.indy.bind.jaxrs;

import org.commonjava.cdi.util.weft.ThreadContext;
import org.commonjava.maven.galley.spi.cache.CacheProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;

@ApplicationScoped
public class ResourceManagementFilter
        implements Filter
{

    public static final String HTTP_REQUEST = "http-request";

    public static final String ORIGINAL_THREAD_NAME = "original-thread-name";

    public static final String METHOD_PATH_TIME = "method-path-time";

    @Inject
    private CacheProvider cacheProvider;

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Override
    public void init( final FilterConfig filterConfig )
            throws ServletException
    {
        if ( logger.isTraceEnabled() )
        {
            cacheProvider.startReporting();
        }
    }

    @Override
    public void doFilter( final ServletRequest request, final ServletResponse response, final FilterChain chain )
            throws IOException, ServletException
    {
        String name = Thread.currentThread().getName();
        String clientAddr = request.getRemoteAddr();

        final HttpServletRequest hsr = (HttpServletRequest) request;
        String tn = hsr.getMethod() + " " + hsr.getPathInfo() + " (" + System.currentTimeMillis() + "." + System.nanoTime() + ")";
        try
        {
            ThreadContext.clearContext();
            ThreadContext threadContext = ThreadContext.getContext( true );
            threadContext.put( ORIGINAL_THREAD_NAME, name );

            threadContext.put( HTTP_REQUEST, hsr );

            threadContext.put( METHOD_PATH_TIME, tn );

            putRequestIDs( hsr, threadContext );

            logger.info( "START request: {} (from: {})", tn, clientAddr );

            Thread.currentThread().setName( tn );
            chain.doFilter( request, response );
        }
        finally
        {
            logger.debug( "Cleaning up resources for thread: {}", Thread.currentThread().getName() );
            Thread.currentThread().setName( name );
            try
            {
                cacheProvider.cleanupCurrentThread();
            }
            catch ( Exception e )
            {
                logger.error( "Failed to cleanup resources", e );
            }

            ThreadContext.clearContext();

            logger.info( "END request: {} (from: {})", tn, clientAddr );

            MDC.clear();
        }
    }

    public static final String HTTP_REQUEST_EXTERNAL_ID = "http-request-external-id";
    public static final String HTTP_REQUEST_INTERNAL_ID = "http-request-internal-id";
    public static final String HTTP_REQUEST_PREFERRED_ID = "http-request-preferred-id";

    /**
     * Put to MDC / threadContext request IDs.
    */
    private void putRequestIDs( HttpServletRequest hsr, ThreadContext threadContext )
    {
        /* We would always generate internalID and provide that in the MDC.
         * If the calling service supplies an externalID, we'd map that under its own key.
         * PreferredID should try to use externalID if it's available, and default over to using internalID if it's not.
         * What this gives us is a single key we can use to reference an ID for the request,
         * and whenever possible it'll reflect the externally supplied ID.
         */
        String internalID = UUID.randomUUID().toString();
        String externalID = hsr.getHeader( HTTP_REQUEST_EXTERNAL_ID );
        String preferredID = externalID != null ? externalID : internalID;

        MDC.put( HTTP_REQUEST_INTERNAL_ID, internalID );
        if ( externalID != null )
        {
            MDC.put( HTTP_REQUEST_EXTERNAL_ID , externalID );
        }
        MDC.put( HTTP_REQUEST_PREFERRED_ID , preferredID );

        /*
         * We should also put the same values in the ThreadContext map, so we can reference them from code without
         * having to go through the logging framework
         */
        threadContext.put( HTTP_REQUEST_INTERNAL_ID, internalID );
        if ( externalID != null )
        {
            threadContext.put(HTTP_REQUEST_EXTERNAL_ID, externalID);
        }
        threadContext.put( HTTP_REQUEST_PREFERRED_ID , preferredID );
    }

    @Override
    public void destroy()
    {
    }

}
