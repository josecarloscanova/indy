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
package org.commonjava.aprox.dotmaven.store.sub;

import static org.commonjava.aprox.dotmaven.util.NameUtils.formatSettingsResourceName;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

import net.sf.webdav.StoredObject;
import net.sf.webdav.exceptions.WebdavException;
import net.sf.webdav.spi.ITransaction;

import org.commonjava.aprox.data.ProxyDataException;
import org.commonjava.aprox.data.StoreDataManager;
import org.commonjava.aprox.dotmaven.DotMavenException;
import org.commonjava.aprox.dotmaven.data.StorageAdvice;
import org.commonjava.aprox.dotmaven.data.StorageAdvisor;
import org.commonjava.aprox.dotmaven.store.SubStore;
import org.commonjava.aprox.dotmaven.util.SettingsTemplate;
import org.commonjava.aprox.dotmaven.util.SettingsURIMatcher;
import org.commonjava.aprox.dotmaven.util.URIMatcher;
import org.commonjava.aprox.dotmaven.webctl.RequestInfo;
import org.commonjava.aprox.model.ArtifactStore;
import org.commonjava.aprox.model.StoreKey;
import org.commonjava.aprox.model.StoreType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@Named( "settings" )
public class SettingsSubStore
    implements SubStore
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    private StoreDataManager aprox;

    @Inject
    private StorageAdvisor advisor;

    @Inject
    private RequestInfo requestInfo;

    @Override
    public boolean matchesUri( final String uri )
    {
        return new SettingsURIMatcher( uri ).matches();
    }

    @Override
    public void createFolder( final ITransaction transaction, final String folderUri )
        throws WebdavException
    {
        throw new WebdavException( "Settings folder is read-only." );
    }

    @Override
    public void createResource( final ITransaction transaction, final String resourceUri )
        throws WebdavException
    {
        throw new WebdavException( "Settings folder is read-only." );
    }

    @Override
    public InputStream getResourceContent( final ITransaction transaction, final String resourceUri )
        throws WebdavException
    {
        final SettingsURIMatcher matcher = new SettingsURIMatcher( resourceUri );
        if ( matcher.isSettingsFileResource() )
        {
            final SettingsTemplate template = getSettingsTemplate( matcher );
            return new ByteArrayInputStream( template.getContent() );
        }

        throw new WebdavException( "File not found: " + resourceUri );
    }

    @Override
    public long setResourceContent( final ITransaction transaction, final String resourceUri, final InputStream content, final String contentType,
                                    final String characterEncoding )
        throws WebdavException
    {
        throw new WebdavException( "Read-only resource." );
    }

    @Override
    public String[] getChildrenNames( final ITransaction transaction, final String folderUri )
        throws WebdavException
    {
        final SettingsURIMatcher matcher = new SettingsURIMatcher( folderUri );

        final Set<String> names = new TreeSet<String>();
        if ( matcher.isSettingsRootResource() )
        {
            for ( final StoreType type : StoreType.values() )
            {
                names.add( type.singularEndpointName() );
            }
        }
        else if ( matcher.isSettingsTypeResource() )
        {
            final StoreType type = matcher.getStoreType();

            List<? extends ArtifactStore> all;
            try
            {
                all = aprox.getAllArtifactStores( type );
            }
            catch ( final ProxyDataException e )
            {
                logger.error( String.format( "Failed to retrieve list of artifact stores: %s", e.getMessage() ), e );
                throw new WebdavException( "Failed to retrieve list of settings configurations." );
            }

            for ( final ArtifactStore store : all )
            {
                final String storeName = formatSettingsResourceName( store.getKey()
                                                                          .getType(), store.getName() );

                //                logger.info( "\n\nCreating settings resource for: '{}'\n\n", storeName );
                names.add( storeName );
            }
        }

        return names.toArray( new String[] {} );
    }

    @Override
    public long getResourceLength( final ITransaction transaction, final String path )
        throws WebdavException
    {
        final SettingsURIMatcher matcher = new SettingsURIMatcher( path );

        if ( matcher.isSettingsFileResource() )
        {
            final SettingsTemplate template = getSettingsTemplate( matcher );
            return template.getLength();
        }

        return 0;
    }

    private synchronized SettingsTemplate getSettingsTemplate( final URIMatcher matcher )
        throws WebdavException
    {
            final StoreKey key = matcher.getStoreKey();
            ArtifactStore store;
            try
            {
                store = aprox.getArtifactStore( key );
            }
            catch ( final ProxyDataException e )
            {
                logger.error( String.format( "Failed to retrieve artifact store: %s. Reason: %s", key, e.getMessage() ), e );
                throw new WebdavException( "Failed to retrieve length for: " + matcher.getURI() );
            }

            StorageAdvice advice;
            try
            {
                advice = advisor.getStorageAdvice( store );
            }
            catch ( final DotMavenException e )
            {
                logger.error( String.format( "Failed to retrieve storage advice for: %s. Reason: %s", key, e.getMessage() ), e );
                throw new WebdavException( "Failed to retrieve length for: " + matcher.getURI() );
            }

        return new SettingsTemplate( key, advice, requestInfo );
    }

    @Override
    public void removeObject( final ITransaction transaction, final String uri )
        throws WebdavException
    {
        throw new WebdavException( "Read-only resource." );
    }

    @Override
    public StoredObject getStoredObject( final ITransaction transaction, final String uri )
        throws WebdavException
    {
        final StoredObject so = new StoredObject();
        final Date d = new Date();
        so.setCreationDate( d );
        so.setLastModified( d );

        final SettingsURIMatcher matcher = new SettingsURIMatcher( uri );
        if ( matcher.isSettingsFileResource() )
        {
            so.setFolder( false );

            final SettingsTemplate st = getSettingsTemplate( matcher );
            so.setResourceLength( st.getLength() );
        }
        else
        {
            so.setFolder( true );
        }

        return so;
    }

    @Override
    public String[] getRootResourceNames()
    {
        return new String[] { "settings" };
    }

}
