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
package org.commonjava.indy.httprox.handler;

import org.commonjava.indy.core.ctl.ContentController;
import org.commonjava.indy.data.StoreDataManager;
import org.commonjava.indy.httprox.conf.HttproxConfig;
import org.commonjava.indy.httprox.keycloak.KeycloakProxyAuthenticator;
import org.commonjava.indy.subsys.template.IndyGroovyException;
import org.commonjava.indy.subsys.template.ScriptEngine;
import org.commonjava.maven.galley.spi.cache.CacheProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.ChannelListener;
import org.xnio.StreamConnection;
import org.xnio.channels.AcceptingChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;

/**
 * Created by jdcasey on 8/13/15.
 */
public class ProxyAcceptHandler
        implements ChannelListener<AcceptingChannel<StreamConnection>>
{
    private static final String HTTPROX_REPO_CREATOR_SCRIPT = "httprox-repo-creator.groovy";

    public static final String HTTPROX_ORIGIN = "httprox";

    @Inject
    private HttproxConfig config;

    @Inject
    private StoreDataManager storeManager;

    @Inject
    private ContentController contentController;

    @Inject
    private KeycloakProxyAuthenticator proxyAuthenticator;

    @Inject
    private CacheProvider cacheProvider;

    @Inject
    private ScriptEngine scriptEngine;

    private ProxyRepositoryCreator creator;

    protected ProxyAcceptHandler()
    {
    }

    public ProxyAcceptHandler( HttproxConfig config, StoreDataManager storeManager, ContentController contentController,
                               KeycloakProxyAuthenticator proxyAuthenticator, CacheProvider cacheProvider,
                               ScriptEngine scriptEngine )
    {
        this.config = config;
        this.storeManager = storeManager;
        this.contentController = contentController;
        this.proxyAuthenticator = proxyAuthenticator;
        this.cacheProvider = cacheProvider;
        this.scriptEngine = scriptEngine;
        init();
    }

    @PostConstruct
    public void init()
    {
        try
        {
            creator = scriptEngine.parseStandardScriptInstance( ScriptEngine.StandardScriptType.store_creators,
                                                                HTTPROX_REPO_CREATOR_SCRIPT, ProxyRepositoryCreator.class );
        }
        catch ( IndyGroovyException e )
        {
            Logger logger = LoggerFactory.getLogger( getClass() );
            logger.error( String.format( "Cannot create ProxyRepositoryCreator instance: %s. Disabling httprox support.",
                                         e.getMessage() ), e );
            config.setEnabled( false );
        }
    }

    @Override
    public void handleEvent( AcceptingChannel<StreamConnection> channel )
    {
        final Logger logger = LoggerFactory.getLogger( getClass() );

        StreamConnection accepted;
        try
        {
            accepted = channel.accept();
        }
        catch ( IOException e )
        {
            logger.error( "Failed to accept httprox connection: " + e.getMessage(), e );
            return;
        }

        if ( accepted == null )
        {
            return;
        }

        logger.debug( "accepted {}", accepted.getPeerAddress() );

        final ConduitStreamSourceChannel source = accepted.getSourceChannel();
        final ConduitStreamSinkChannel sink = accepted.getSinkChannel();

        final ProxyResponseWriter writer =
                new ProxyResponseWriter( config, storeManager, contentController, proxyAuthenticator, cacheProvider,
                                         creator );

        logger.debug( "Setting writer: {}", writer );
        sink.getWriteSetter().set( writer );

        final ProxyRequestReader reader = new ProxyRequestReader( writer, sink );

        logger.debug( "Setting reader: {}", reader );
        source.getReadSetter().set( reader );

        source.resumeReads();

    }

}
