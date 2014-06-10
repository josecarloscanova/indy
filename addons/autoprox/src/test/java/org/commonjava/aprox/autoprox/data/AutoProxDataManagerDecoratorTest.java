/*******************************************************************************
 * Copyright (c) 2014 Red Hat, Inc..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.commonjava.aprox.autoprox.data;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.commonjava.aprox.autoprox.conf.AutoProxConfig;
import org.commonjava.aprox.autoprox.conf.FactoryMapping;
import org.commonjava.aprox.autoprox.fixture.HttpTestFixture;
import org.commonjava.aprox.autoprox.fixture.TestAutoProxFactory;
import org.commonjava.aprox.autoprox.fixture.TestAutoProxyDataManager;
import org.commonjava.aprox.autoprox.inject.AutoProxCatalogProducer;
import org.commonjava.aprox.autoprox.model.AutoProxCatalog;
import org.commonjava.aprox.data.StoreDataManager;
import org.commonjava.aprox.model.Group;
import org.commonjava.aprox.model.RemoteRepository;
import org.commonjava.aprox.model.StoreKey;
import org.commonjava.aprox.model.StoreType;
import org.commonjava.aprox.subsys.flatfile.conf.FlatFileConfiguration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoProxDataManagerDecoratorTest
{

    public static final String REPO_ROOT_DIR = "repo.root.dir";

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Rule
    public final HttpTestFixture http = new HttpTestFixture( "server-targets" );

    private final AutoProxCatalog catalog = new AutoProxCatalog( true, new ArrayList<FactoryMapping>() );

    private final StoreDataManager proxyManager = new TestAutoProxyDataManager( catalog, http.getHttp() );

    @Before
    public final void setup()
        throws Exception
    {
        proxyManager.install();
        proxyManager.clear();
    }

    @Test
    public void repositoryCreatedFromScannedDataDirRules()
        throws Exception
    {
        final AutoProxConfig apConfig = new AutoProxConfig( "autoprox", true, Collections.<FactoryMapping> emptyList() );
        final FlatFileConfiguration ffConfig = new FlatFileConfiguration();

        System.setProperty( "baseUrl", http.getBaseUri() );

        final URL u = Thread.currentThread()
                            .getContextClassLoader()
                            .getResource( "data/autoprox/simple-factory.groovy" );
        File f = new File( u.getPath() );
        f = f.getParentFile()
             .getParentFile();

        ffConfig.setDataBasedir( f );

        final AutoProxCatalog catalog = new AutoProxCatalogProducer( ffConfig, apConfig ).getCatalog();
        final StoreDataManager proxyManager = new TestAutoProxyDataManager( catalog, http.getHttp() );

        final String testUrl = http.formatUrl( "target", "test" );
        http.get( testUrl, 404 );
        http.expect( testUrl, 200 );
        //        targetResponder.approveTargets( "test" );
        http.get( testUrl, 200 );

        catalog.setEnabled( false );
        assertThat( proxyManager.getRemoteRepository( "test" ), nullValue() );
        catalog.setEnabled( true );

        final RemoteRepository repo = proxyManager.getRemoteRepository( "test" );

        assertThat( repo, notNullValue() );
        assertThat( repo.getName(), equalTo( "test" ) );
        assertThat( repo.getUrl(), equalTo( testUrl ) );
    }

    @Test
    public void repositoryNOTCreatedFromScannedDataDirRulesWhenNameNotTest()
        throws Exception
    {
        final AutoProxConfig apConfig = new AutoProxConfig( "autoprox", true, Collections.<FactoryMapping> emptyList() );
        final FlatFileConfiguration ffConfig = new FlatFileConfiguration();

        System.setProperty( "baseUrl", http.getBaseUri() );

        final URL u = Thread.currentThread()
                            .getContextClassLoader()
                            .getResource( "data/autoprox/simple-factory.groovy" );
        File f = new File( u.getPath() );
        f = f.getParentFile()
             .getParentFile();

        ffConfig.setDataBasedir( f );

        final AutoProxCatalog catalog = new AutoProxCatalogProducer( ffConfig, apConfig ).getCatalog();
        final StoreDataManager proxyManager = new TestAutoProxyDataManager( catalog, http.getHttp() );

        final String testUrl = http.formatUrl( "target", "test" );
        http.get( testUrl, 404 );
        http.expect( testUrl, 200 );
        //        targetResponder.approveTargets( "test" );
        http.get( testUrl, 200 );

        catalog.setEnabled( false );
        assertThat( proxyManager.getRemoteRepository( "foo" ), nullValue() );
        catalog.setEnabled( true );

        final RemoteRepository repo = proxyManager.getRemoteRepository( "foo" );

        assertThat( repo, nullValue() );
    }

    @Test
    public void repositoryAutoCreated()
        throws Exception
    {
        simpleCatalog();

        final String testUrl = http.formatUrl( "target", "test" );
        http.get( testUrl, 404 );
        http.expect( testUrl, 200 );
        //        targetResponder.approveTargets( "test" );
        http.get( testUrl, 200 );

        catalog.setEnabled( false );
        assertThat( proxyManager.getRemoteRepository( "test" ), nullValue() );
        catalog.setEnabled( true );

        final RemoteRepository repo = proxyManager.getRemoteRepository( "test" );

        assertThat( repo, notNullValue() );
        assertThat( repo.getName(), equalTo( "test" ) );
        assertThat( repo.getUrl(), equalTo( testUrl ) );

    }

    private void simpleCatalog()
    {
        final TestAutoProxFactory fac = new TestAutoProxFactory( http );
        catalog.getFactoryMappings()
               .add( new FactoryMapping( "test.groovy", fac ) );
    }

    @Test
    public void groupAutoCreatedWithDeployPointAndTwoRepos()
        throws Exception
    {
        simpleCatalog();

        final String testUrl = http.formatUrl( "target", "test" );
        http.get( testUrl, 404 );

        http.expect( testUrl, 200 );
        http.expect( http.formatUrl( "target", "first" ), 200 );
        http.expect( http.formatUrl( "target", "second" ), 200 );
        //        targetResponder.approveTargets( "test" );
        http.get( testUrl, 200 );

        catalog.setEnabled( false );
        assertThat( proxyManager.getGroup( "test" ), nullValue() );
        catalog.setEnabled( true );

        final Group group = proxyManager.getGroup( "test" );

        assertThat( group, notNullValue() );
        assertThat( group.getName(), equalTo( "test" ) );

        final List<StoreKey> constituents = group.getConstituents();

        logger.info( "Group constituents: {}", constituents );

        assertThat( constituents, notNullValue() );
        assertThat( constituents.size(), equalTo( 4 ) );

        int idx = 0;
        StoreKey key = constituents.get( idx );

        assertThat( key.getType(), equalTo( StoreType.hosted ) );
        assertThat( key.getName(), equalTo( "test" ) );

        idx++;
        key = constituents.get( idx );

        assertThat( key.getType(), equalTo( StoreType.remote ) );
        assertThat( key.getName(), equalTo( "test" ) );

        idx++;
        key = constituents.get( idx );

        assertThat( key.getType(), equalTo( StoreType.remote ) );
        assertThat( key.getName(), equalTo( "first" ) );

        idx++;
        key = constituents.get( idx );

        assertThat( key.getType(), equalTo( StoreType.remote ) );
        assertThat( key.getName(), equalTo( "second" ) );
    }

    @Test
    public void repositoryNotAutoCreatedWhenTargetIsInvalid()
        throws Exception
    {
        simpleCatalog();

        final String testUrl = http.formatUrl( "target", "test" );
        http.get( testUrl, 404 );

        catalog.setEnabled( false );
        assertThat( proxyManager.getRemoteRepository( "test" ), nullValue() );
        catalog.setEnabled( true );

        final RemoteRepository repo = proxyManager.getRemoteRepository( "test" );

        assertThat( repo, nullValue() );

    }

    @Test
    public void groupNotAutoCreatedWhenTargetIsInvalid()
        throws Exception
    {
        simpleCatalog();

        final String testUrl = http.formatUrl( "target", "test" );
        http.get( testUrl, 404 );

        catalog.setEnabled( false );
        assertThat( proxyManager.getGroup( "test" ), nullValue() );
        catalog.setEnabled( true );

        final Group group = proxyManager.getGroup( "test" );

        assertThat( group, nullValue() );
    }

}