/*******************************************************************************
 * Copyright 2011 John Casey
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.commonjava.aprox.core.live.rest.access;

import static org.apache.commons.io.FileUtils.forceDelete;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;

import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.commonjava.aprox.core.data.ProxyDataException;
import org.commonjava.aprox.core.live.AbstractAProxLiveTest;
import org.commonjava.aprox.core.live.fixture.ProxyConfigProvider;
import org.commonjava.aprox.core.model.StoreKey;
import org.commonjava.aprox.core.model.StoreType;
import org.commonjava.web.test.fixture.TestWarArchiveBuilder;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith( Arquillian.class )
public class GroupAccessResourceLiveTest
    extends AbstractAProxLiveTest
{

    private static final String BASE_URL = "/group/test/";

    private static File repoRoot;

    @Deployment
    public static WebArchive createWar()
    {
        return new TestWarArchiveBuilder( GroupAccessResourceLiveTest.class ).withExtraClasses( AbstractAProxLiveTest.class,
                                                                                                ProxyConfigProvider.class )
                                                                             .withLibrariesIn( new File(
                                                                                                         "target/dependency" ) )
                                                                             .withLog4jProperties()
                                                                             .build();
    }

    @BeforeClass
    public static void setRepoRootDir()
        throws IOException
    {
        repoRoot = File.createTempFile( "repo.root.", ".dir" );
        System.setProperty( ProxyConfigProvider.REPO_ROOT_DIR, repoRoot.getAbsolutePath() );
    }

    @AfterClass
    public static void clearRepoRootDir()
        throws IOException
    {
        if ( repoRoot != null && repoRoot.exists() )
        {
            forceDelete( repoRoot );
        }
    }

    @Before
    public void setupTest()
        throws ProxyDataException
    {
        proxyManager.storeRepository( modelFactory.createRepository( "dummy", "http://www.nowhere.com/" ) );
        proxyManager.storeRepository( modelFactory.createRepository( "central", "http://repo1.maven.apache.org/maven2/" ) );

        proxyManager.storeGroup( modelFactory.createGroup( "test", new StoreKey( StoreType.repository, "dummy" ),
                                                           new StoreKey( StoreType.repository, "central" ) ) );
    }

    @Test
    public void retrievePOMFromRepositoryGroupWithDummyFirstRepository()
        throws ClientProtocolException, IOException
    {
        final String response =
            webFixture.getString( webFixture.resourceUrl( BASE_URL,
                                                          "org/apache/maven/maven-model/3.0.3/maven-model-3.0.3.pom" ),
                                  HttpStatus.SC_OK );
        assertThat( response.contains( "<artifactId>maven-model</artifactId>" ), equalTo( true ) );
    }

}