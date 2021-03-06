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
package org.commonjava.indy.content.index;

import org.commonjava.indy.IndyWorkflowException;
import org.commonjava.indy.content.ContentManager;
import org.commonjava.indy.data.IndyDataException;
import org.commonjava.indy.data.StoreDataManager;
import org.commonjava.indy.model.core.ArtifactStore;
import org.commonjava.indy.model.core.Group;
import org.commonjava.indy.model.core.StoreKey;
import org.commonjava.indy.model.core.StoreType;
import org.commonjava.indy.model.galley.KeyedLocation;
import org.commonjava.indy.util.LocationUtils;
import org.commonjava.maven.galley.event.EventMetadata;
import org.commonjava.maven.galley.model.ConcreteResource;
import org.commonjava.maven.galley.model.SpecialPathInfo;
import org.commonjava.maven.galley.model.Transfer;
import org.commonjava.maven.galley.model.TransferOperation;
import org.commonjava.maven.galley.spi.io.SpecialPathManager;
import org.commonjava.maven.galley.spi.nfc.NotFoundCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.decorator.Decorator;
import javax.decorator.Delegate;
import javax.enterprise.inject.Any;
import javax.inject.Inject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Decorator for ContentManager which uses Infinispan to index content to avoid having to iterate all members of large
 * groups looking for a file.
 *
 * Created by jdcasey on 3/15/16.
 */
@Decorator
public abstract class IndexingContentManagerDecorator
        implements ContentManager
{
    @Inject
    private StoreDataManager storeDataManager;

    @Inject
    private SpecialPathManager specialPathManager;

    @Delegate
    @Any
    @Inject
    private ContentManager delegate;

    @Inject
    private ContentIndexManager indexManager;

    @Inject
    private NotFoundCache nfc;

    protected IndexingContentManagerDecorator()
    {
    }

    protected IndexingContentManagerDecorator( final ContentManager delegate, final StoreDataManager storeDataManager,
                                               final SpecialPathManager specialPathManager,
                                               final ContentIndexManager indexManager, final NotFoundCache nfc )
    {
        this.delegate = delegate;
        this.storeDataManager = storeDataManager;
        this.specialPathManager = specialPathManager;
        this.indexManager = indexManager;
        this.nfc = nfc;
    }

    @Override
    public Transfer retrieveFirst( final List<? extends ArtifactStore> stores, final String path )
            throws IndyWorkflowException
    {
        return retrieveFirst( stores, path, new EventMetadata() );
    }

    @Override
    public Transfer retrieveFirst( final List<? extends ArtifactStore> stores, final String path,
                                   final EventMetadata eventMetadata )
            throws IndyWorkflowException
    {
        Transfer transfer = null;
        for ( ArtifactStore store : stores )
        {
            transfer = retrieve( store, path, eventMetadata );
            if ( transfer != null )
            {
                break;
            }
        }

        return transfer;
    }

    @Override
    public List<Transfer> retrieveAll( final List<? extends ArtifactStore> stores, final String path )
            throws IndyWorkflowException
    {
        return retrieveAll( stores, path, new EventMetadata() );
    }

    @Override
    public List<Transfer> retrieveAll( final List<? extends ArtifactStore> stores, final String path,
                                       final EventMetadata eventMetadata )
            throws IndyWorkflowException
    {
        List<Transfer> results = new ArrayList<>();
        stores.stream().map( ( store ) -> {
            try
            {
                return retrieve( store, path, eventMetadata );
            }
            catch ( IndyWorkflowException e )
            {
                Logger logger = LoggerFactory.getLogger( getClass() );
                logger.error(
                        String.format( "Failed to retrieve indexed content: %s:%s. Reason: %s", store.getKey(), path,
                                       e.getMessage() ), e );
            }

            return null;
        } ).filter( ( transfer ) -> transfer != null ).forEachOrdered( ( transfer ) -> {
            if ( transfer != null )
            {
                results.add( transfer );
            }
        } );

        return results;
    }

    @Override
    public Transfer retrieve( final ArtifactStore store, final String path )
            throws IndyWorkflowException
    {
        return retrieve( store, path, new EventMetadata() );
    }

    @Override
    public Transfer retrieve( final ArtifactStore store, final String path, final EventMetadata eventMetadata )
            throws IndyWorkflowException
    {
        Logger logger = LoggerFactory.getLogger( getClass() );

        logger.trace( "Looking for indexed path: {} in: {}", path, store.getKey() );

        Transfer transfer = getIndexedTransfer( store.getKey(), null, path, TransferOperation.DOWNLOAD );
        if ( transfer != null )
        {
            logger.debug( "Found indexed transfer: {}. Returning.", transfer );
            return transfer;
        }

        StoreType type = store.getKey().getType();

        // NOTE: This will make the index cache non-disposable, which will mean that we have to use more reliable
        // (slower) disk to store it...which will be BAD for performance.
        // Ironically, this change will speed things up in the short term but slow them way down in the larger
        // context.
//        if ( StoreType.hosted == type )
//        {
//            // hosted repos are completely indexed, since the store() method maintains the index
//            // So, if it wasn't found in the index (above), and we're looking at a hosted repo, it's not here.
//            logger.debug( "HOSTED / Not-Indexed: {}/{}", store.getKey(), path );
//            return null;
//        }
//        else if ( StoreType.group == type )

        if ( StoreType.group == type )
        {
            ConcreteResource resource = new ConcreteResource( LocationUtils.toLocation( store ), path );
            if ( nfc.isMissing( resource ) )
            {
                logger.debug( "{} is marked as missing. Returning null.", resource );
                return null;
            }

            logger.debug( "No group index hits. Devolving to member store indexes." );

            KeyedLocation location = LocationUtils.toLocation( store );
            SpecialPathInfo specialPathInfo = specialPathManager.getSpecialPathInfo( location, path );
            if ( specialPathInfo == null || !specialPathInfo.isMergable() )
            {
                transfer = getIndexedMemberTransfer( (Group) store, path, TransferOperation.DOWNLOAD,
                                                     ( member ) -> {
                                                         try
                                                         {
                                                             return delegate.retrieve( member, path );
                                                         }
                                                         catch ( IndyWorkflowException e )
                                                         {
                                                             logger.error( String.format(
                                                                     "Failed to retrieve() for member path: %s:%s. Reason: %s",
                                                                     member.getKey(), path, e.getMessage() ), e );
                                                         }

                                                         return null;
                                                     } );

                if ( transfer != null )
                {
                    nfc.clearMissing( resource );
                    return transfer;
                }
                logger.debug( "No index hits. Delegating to main content manager for: {} in: {}", path, store );
            }
            else
            {
                logger.debug( "Merged content. Delegating to main content manager for: {} in: {}", path, store );
                transfer = delegate.retrieve( store, path, eventMetadata );
                if ( transfer == null )
                {
                    nfc.addMissing( resource );
                }

                return transfer;
            }
        }

        transfer = delegate.retrieve( store, path, eventMetadata );

        if ( transfer != null )
        {
            logger.debug( "Got transfer from delegate: {} (will index)", transfer );

            indexManager.indexTransferIn( transfer, store.getKey() );
        }

        logger.debug( "Returning transfer: {}", transfer );
        return transfer;
    }

    private Transfer getIndexedTransfer( final StoreKey storeKey, final StoreKey topKey, final String path, final TransferOperation op )
            throws IndyWorkflowException
    {
        Logger logger = LoggerFactory.getLogger( getClass() );
        logger.trace( "Looking for indexed path: {} in: {} (entry point: {})", path, storeKey, topKey );

        IndexedStorePath storePath = indexManager.getIndexedStorePath( storeKey, path );

        if ( storePath != null )
        {
            Transfer transfer = delegate.getTransfer( storeKey, path, op );
            if ( transfer == null || !transfer.exists() )
            {
                logger.trace( "Found obsolete index entry: {}. De-indexing from: {} and {}", storeKey, topKey );
                // something happened to the underlying Transfer...de-index it, and don't return it.
                indexManager.deIndexStorePath( storeKey, path );
                if ( topKey != null )
                {
                    indexManager.deIndexStorePath( topKey, path );
                }
            }
            else
            {
                logger.trace( "Found it!" );
                return transfer;
            }
        }

        return null;
    }

    @Override
    public Transfer getTransfer( final ArtifactStore store, final String path, final TransferOperation op )
            throws IndyWorkflowException
    {
        Logger logger = LoggerFactory.getLogger( getClass() );

        Transfer transfer = getIndexedTransfer( store.getKey(), null, path, TransferOperation.DOWNLOAD );
        if ( transfer != null )
        {
            return transfer;
        }

        ConcreteResource resource = new ConcreteResource( LocationUtils.toLocation( store ), path );
        StoreType type = store.getKey().getType();

        if ( StoreType.group == type )
        {
            if ( !nfc.isMissing( resource ) )
            {
                logger.debug( "No group index hits. Devolving to member store indexes." );
                for ( StoreKey key : ( (Group) store ).getConstituents() )
                {
                    transfer = getIndexedMemberTransfer( (Group) store, path, op, (member)->{
                        try
                        {
                            return delegate.getTransfer( member, path, op );
                        }
                        catch ( IndyWorkflowException e )
                        {
                            logger.error( String.format(
                                    "Failed to getTransfer() for: %s:%s with operation: %s. Reason: %s",
                                    member.getKey(), path, op, e.getMessage() ), e );
                        }

                        return null;
                    } );
                    if ( transfer != null )
                    {
                        return transfer;
                    }
                }
            }
            else
            {
                logger.debug( "NFC marks {} as missing. Returning null.", resource );
                return null;
            }
        }

        transfer = delegate.getTransfer( store, path, op );
        // index the transfer only if it exists, it cannot be null at this point
        if ( transfer != null && transfer.exists() )
        {
            indexManager.indexTransferIn( transfer, store.getKey() );
        }

        logger.debug( "Returning transfer: {}", transfer );
        return transfer;
    }

    private Transfer getIndexedMemberTransfer( final Group group, final String path, TransferOperation op, ContentManagementFunction func )
            throws IndyWorkflowException
    {
        StoreKey topKey = group.getKey();

        List<StoreKey> toProcess = new ArrayList<>( group.getConstituents() );
        Set<StoreKey> seen = new HashSet<>();

        Transfer transfer = null;
        while ( !toProcess.isEmpty() )
        {
            StoreKey key = toProcess.remove( 0 );

            seen.add( key );

            ArtifactStore member = null;
            try
            {
                member = storeDataManager.getArtifactStore( key );
                if ( member == null )
                {
                    continue;
                }
            }
            catch ( IndyDataException e )
            {
                Logger logger = LoggerFactory.getLogger( getClass() );
                logger.error(
                        String.format( "Failed to lookup store: %s (in membership of: %s). Reason: %s", key,
                                       topKey, e.getMessage() ), e );
            }

            transfer = getIndexedTransfer( key, topKey, path, op );
            if ( transfer == null && StoreType.group != key.getType() )
            {
                // don't call this for constituents that are groups...we'll manually traverse the membership below...
                transfer = func.apply( member );
            }

            if ( transfer != null )
            {
                indexManager.indexTransferIn( transfer, key, topKey );
                return transfer;
            }
            else if( StoreType.group == key.getType() )
            {
                int i=0;
                for ( StoreKey memberKey : ((Group)member).getConstituents() )
                {
                    if ( !seen.contains( memberKey ) )
                    {
                        toProcess.add( i, memberKey );
                        i++;
                    }
                }
            }

        }

        return transfer;
    }

    @Override
    public Transfer getTransfer( final StoreKey storeKey, final String path, final TransferOperation op )
            throws IndyWorkflowException
    {
        Logger logger = LoggerFactory.getLogger( getClass() );

        Transfer transfer = getIndexedTransfer( storeKey, null, path, TransferOperation.DOWNLOAD );
        if ( transfer != null )
        {
            logger.debug( "Returning indexed transfer: {}", transfer );
            return transfer;
        }

        ArtifactStore store;
        try
        {
            store = storeDataManager.getGroup( storeKey.getName() );
        }
        catch ( IndyDataException e )
        {
            throw new IndyWorkflowException( "Failed to lookup ArtifactStore: %s for NFC handling. Reason: %s", e,
                                             storeKey, e.getMessage() );
        }

        ConcreteResource resource = new ConcreteResource( LocationUtils.toLocation( store ), path );
        StoreType type = storeKey.getType();

        if ( StoreType.group == type )
        {
            Group g= (Group) store;

            if ( g == null )
            {
                throw new IndyWorkflowException( "Cannot find requested group: %s", storeKey );
            }

            if ( nfc.isMissing( resource ) )
            {
                logger.debug( "NFC / MISSING: {}", resource );
                return null;
            }

            logger.debug( "No group index hits. Devolving to member store indexes." );
            for ( StoreKey key : g.getConstituents() )
            {
                transfer = getIndexedMemberTransfer( (Group) store, path, op,
                                                     ( member ) -> {
                                                         try
                                                         {
                                                             return delegate.getTransfer( member, path,
                                                                                   op );
                                                         }
                                                         catch ( IndyWorkflowException e )
                                                         {
                                                             logger.error( String.format(
                                                                     "Failed to getTransfer() for member path: %s:%s with operation: %s. Reason: %s",
                                                                     member.getKey(), path, op, e.getMessage() ), e );
                                                         }

                                                         return null;
                                                     } );
                if ( transfer != null )
                {
                    logger.debug( "Returning indexed transfer: {} from member: {}", transfer, key );
                    return transfer;
                }
            }
        }

        transfer = delegate.getTransfer( storeKey, path, op );
        if ( transfer != null )
        {
            logger.debug( "Indexing transfer: {}", transfer );
            indexManager.indexTransferIn( transfer, storeKey );
        }

        return transfer;
    }

    @Override
    public Transfer getTransfer( final List<ArtifactStore> stores, final String path, final TransferOperation op )
            throws IndyWorkflowException
    {
        Transfer transfer = null;
        for ( ArtifactStore store : stores )
        {
            transfer = getTransfer( store, path, op );
            if ( transfer != null )
            {
                break;
            }
        }

        return transfer;
    }

    @Override
    public Transfer store( final ArtifactStore store, final String path, final InputStream stream,
                           final TransferOperation op )
            throws IndyWorkflowException
    {
        return store( store, path, stream, op, new EventMetadata() );
    }

    @Override
    public Transfer store( final ArtifactStore store, final String path, final InputStream stream,
                           final TransferOperation op, final EventMetadata eventMetadata )
            throws IndyWorkflowException
    {
        Logger logger = LoggerFactory.getLogger( getClass() );
        logger.trace( "Storing: {} in: {}", path, store.getKey() );
        Transfer transfer = delegate.store( store, path, stream, op, eventMetadata );
        if ( transfer != null )
        {
            logger.trace( "Indexing: {} in: {}", transfer, store.getKey() );
            indexManager.indexTransferIn( transfer, store.getKey() );

            if ( store instanceof Group )
            {
                nfc.clearMissing( new ConcreteResource( LocationUtils.toLocation( store ), path ) );
            }
        }

        return transfer;
    }

    //    @Override
    //    public Transfer store( final List<? extends ArtifactStore> stores, final String path, final InputStream stream, final TransferOperation op )
    //            throws IndyWorkflowException
    //    {
    //        return store( stores, path, stream, op, new EventMetadata() );
    //    }

    @Override
    public Transfer store( final List<? extends ArtifactStore> stores, final StoreKey topKey, final String path,
                           final InputStream stream, final TransferOperation op, final EventMetadata eventMetadata )
            throws IndyWorkflowException
    {
        Transfer transfer = delegate.store( stores, topKey, path, stream, op, eventMetadata );
        if ( transfer != null )
        {
            indexManager.indexTransferIn( transfer, topKey );

            try
            {
                ArtifactStore topStore = storeDataManager.getArtifactStore( topKey );
                nfc.clearMissing( new ConcreteResource( LocationUtils.toLocation( topStore ), path ) );
            }
            catch ( IndyDataException e )
            {
                Logger logger = LoggerFactory.getLogger( getClass() );
                logger.error( String.format( "Failed to retrieve top store: %s for NFC management. Reason: %s",
                                             topKey, e.getMessage()), e );
            }
        }

        return transfer;
    }

    @Override
    public boolean delete( final ArtifactStore store, final String path )
            throws IndyWorkflowException
    {
        return delete( store, path, new EventMetadata() );
    }

    @Override
    public boolean delete( final ArtifactStore store, final String path, final EventMetadata eventMetadata )
            throws IndyWorkflowException
    {
        boolean result = delegate.delete( store, path, eventMetadata );
        if ( result )
        {
            indexManager.deIndexStorePath( store.getKey(), path );
        }

        return result;
    }

    @Override
    public boolean deleteAll( final List<? extends ArtifactStore> stores, final String path )
            throws IndyWorkflowException
    {
        return deleteAll( stores, path, new EventMetadata() );
    }

    @Override
    public boolean deleteAll( final List<? extends ArtifactStore> stores, final String path,
                              final EventMetadata eventMetadata )
            throws IndyWorkflowException
    {
        boolean result = false;
        for ( ArtifactStore store : stores )
        {
            result = delete( store, path, eventMetadata ) | result;
        }

        return result;
    }

    private interface ContentManagementFunction
    {
        Transfer apply(ArtifactStore store);
    }

}
