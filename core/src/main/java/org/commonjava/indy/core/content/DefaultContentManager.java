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
package org.commonjava.indy.core.content;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.commonjava.indy.IndyWorkflowException;
import org.commonjava.indy.content.ArtifactData;
import org.commonjava.indy.content.ContentDigest;
import org.commonjava.indy.content.ContentDigester;
import org.commonjava.indy.content.ContentGenerator;
import org.commonjava.indy.content.ContentManager;
import org.commonjava.indy.content.DownloadManager;
import org.commonjava.indy.content.StoreResource;
import org.commonjava.indy.data.IndyDataException;
import org.commonjava.indy.data.StoreDataManager;
import org.commonjava.indy.model.core.AbstractRepository;
import org.commonjava.indy.model.core.ArtifactStore;
import org.commonjava.indy.model.core.Group;
import org.commonjava.indy.model.core.StoreKey;
import org.commonjava.indy.model.core.io.IndyObjectMapper;
import org.commonjava.indy.model.galley.KeyedLocation;
import org.commonjava.indy.util.ApplicationStatus;
import org.commonjava.indy.util.LocationUtils;
import org.commonjava.maven.galley.event.EventMetadata;
import org.commonjava.maven.galley.model.SpecialPathInfo;
import org.commonjava.maven.galley.model.Transfer;
import org.commonjava.maven.galley.model.TransferOperation;
import org.commonjava.maven.galley.spi.io.SpecialPathManager;
import org.commonjava.maven.galley.spi.nfc.NotFoundCache;
import org.commonjava.maven.galley.transport.htcli.model.HttpExchangeMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.commonjava.indy.model.core.StoreType.group;
import static org.commonjava.indy.util.ContentUtils.dedupeListing;

public class DefaultContentManager
        implements ContentManager
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    private Instance<ContentGenerator> contentProducerInstances;

    private Set<ContentGenerator> contentGenerators;

    @Inject
    private StoreDataManager storeManager;

    @Inject
    private DownloadManager downloadManager;

    @Inject
    private SpecialPathManager specialPathManager;

    @Inject
    private NotFoundCache nfc;

    @Inject
    private IndyObjectMapper mapper;

    @Inject
    private ContentDigester contentDigester;

    protected DefaultContentManager()
    {
    }

    public DefaultContentManager( final StoreDataManager storeManager, final DownloadManager downloadManager,
                                  final IndyObjectMapper mapper, final SpecialPathManager specialPathManager,
                                  final NotFoundCache nfc, final ContentDigester contentDigester, final Set<ContentGenerator> contentProducers )
    {
        this.storeManager = storeManager;
        this.downloadManager = downloadManager;
        this.mapper = mapper;
        this.specialPathManager = specialPathManager;
        this.nfc = nfc;
        this.contentDigester = contentDigester;
        this.contentGenerators = contentProducers == null ? new HashSet<ContentGenerator>() : contentProducers;
    }

    @PostConstruct
    public void initialize()
    {
        contentGenerators = new HashSet<ContentGenerator>();
        if ( contentProducerInstances != null )
        {
            for ( final ContentGenerator producer : contentProducerInstances )
            {
                contentGenerators.add( producer );
            }
        }
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
        Transfer txfr = null;
        for ( final ArtifactStore store : stores )
        {
            txfr = doRetrieve( store, path, eventMetadata );
            if ( txfr != null )
            {
                break;
            }
        }

        return txfr;
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
        final List<Transfer> txfrs = new ArrayList<Transfer>();
        for ( final ArtifactStore store : stores )
        {
            if ( group == store.getKey().getType() )
            {
                List<ArtifactStore> members;
                try
                {
                    members = storeManager.getOrderedConcreteStoresInGroup( store.getName(), false );
                }
                catch ( final IndyDataException e )
                {
                    throw new IndyWorkflowException( "Failed to lookup concrete members of: %s. Reason: %s", e, store,
                                                      e.getMessage() );
                }

                final List<Transfer> storeTransfers = new ArrayList<Transfer>();
                for ( final ContentGenerator generator : contentGenerators )
                {
                    final Transfer txfr =
                            generator.generateGroupFileContent( (Group) store, members, path, eventMetadata );
                    if ( txfr != null )
                    {
                        storeTransfers.add( txfr );
                    }
                }

                // If the content was generated, don't try to retrieve it from a member store...this is the lone exception to retrieveAll
                // ...if it's generated, it's merged in this case.
                if ( storeTransfers.isEmpty() )
                {
                    for ( final ArtifactStore member : members )
                    {
                        // NOTE: This is only safe to call because we're concrete ordered stores, so anything passing through here is concrete.
                        final Transfer txfr = doRetrieve( member, path, eventMetadata );
                        if ( txfr != null )
                        {
                            storeTransfers.add( txfr );
                        }
                    }
                }

                txfrs.addAll( storeTransfers );
            }
            else
            {
                // NOTE: This is only safe to call because we're doing the group check up front, so anything passing through here is concrete.
                final Transfer txfr = doRetrieve( store, path, eventMetadata );
                if ( txfr != null )
                {
                    txfrs.add( txfr );
                }
            }
        }

        return txfrs;
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
        Transfer item;
        if ( group == store.getKey().getType() )
        {
            List<ArtifactStore> members;
            try
            {
                members = storeManager.getOrderedConcreteStoresInGroup( store.getName(), true );
            }
            catch ( final IndyDataException e )
            {
                throw new IndyWorkflowException( "Failed to lookup concrete members of: %s. Reason: %s", e, store,
                                                  e.getMessage() );
            }

            if ( logger.isDebugEnabled() )
            {
                logger.debug( "{} is a group. Attempting downloads from (in order):\n  {}", store.getKey(), StringUtils.join(members, "\n  ") );
            }

            item = null;
            boolean generated = false;
            for ( final ContentGenerator generator : contentGenerators )
            {
                if ( generator.canProcess( path ) )
                {
                    item = generator.generateGroupFileContent( (Group) store, members, path, eventMetadata );
                    logger.debug( "From content {}.generateGroupFileContent: {} (exists? {})",
                                  generator.getClass().getSimpleName(), item, item != null && item.exists() );
                    generated = true;
                    break;
                }
            }

            if ( !generated )
            {
                for ( final ArtifactStore member : members )
                {
                    try
                    {
                        item = doRetrieve( member, path, eventMetadata );
                    }
                    catch ( IndyWorkflowException e )
                    {
                        logger.error( "Failed to retrieve artifact from for path {} from {} in group {}, error is: {}",
                                      path, member, store, e.getMessage() );
                    }
                    if ( item != null )
                    {
                        break;
                    }
                }
            }
        }
        else
        {
            item = doRetrieve( store, path, eventMetadata );
        }

        logger.info( "Returning transfer: {}", item );

        return item;
    }

    private boolean checkMask( final ArtifactStore store, final String path )
    {
        if ( !( store instanceof AbstractRepository ) )
        {
            return true;
        }

        AbstractRepository repo = (AbstractRepository) store;
        Set<String> maskPatterns = repo.getPathMaskPatterns();
        logger.debug( "Checking mask in: {}, type: {}, patterns: {}", repo.getName(), repo.getKey().getType(), maskPatterns );

        if (maskPatterns == null || maskPatterns.isEmpty())
        {
            logger.debug( "Checking mask in: {}, - NO PATTERNS", repo.getName() );
            return true;
        }
        for (String pattern : maskPatterns)
        {
            if (path.startsWith(pattern) || path.matches(pattern))
            {
                logger.debug( "Checking mask in: {}, pattern: {} - MATCH", repo.getName(), pattern );
                return true;
            }
        }
        return false;
    }

    private Transfer doRetrieve( final ArtifactStore store, final String path, final EventMetadata eventMetadata )
            throws IndyWorkflowException
    {
        logger.info( "Attempting to retrieve: {} from: {}", path, store.getKey() );

        if ( !checkMask(store, path))
        {
            return null;
        }

        Transfer item = null;
        try
        {
            item = downloadManager.retrieve( store, path, eventMetadata );

            if ( item == null )
            {
                for ( final ContentGenerator generator : contentGenerators )
                {
                    logger.debug( "Attempting to generate content for path: {} in: {} via: {}", path, store,
                                  generator );
                    item = generator.generateFileContent( store, path, eventMetadata );
                    if ( item != null )
                    {
                        break;
                    }
                }
            }
        }
        catch ( IndyWorkflowException e )
        {
            e.filterLocationErrors();
        }

        return item;
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
        if ( group == store.getKey().getType() )
        {
            try
            {
                final List<ArtifactStore> allMembers = storeManager.getOrderedConcreteStoresInGroup( store.getName(), false );

                final Transfer txfr = store( allMembers, store.getKey(), path, stream, op, eventMetadata );
                logger.info( "Stored: {} for group: {} in: {}", path, store.getKey(), txfr );
                return txfr;
            }
            catch ( final IndyDataException e )
            {
                throw new IndyWorkflowException( "Failed to lookup concrete members of: %s. Reason: %s", e, store,
                                                  e.getMessage() );
            }
        }

        logger.info( "Storing: {} for: {} with event metadata: {}", path, store.getKey(), eventMetadata );
        final Transfer txfr = downloadManager.store( store, path, stream, op, eventMetadata );
        if ( txfr != null )
        {
            final KeyedLocation kl = (KeyedLocation) txfr.getLocation();
            ArtifactStore transferStore;
            try
            {
                transferStore = storeManager.getArtifactStore( kl.getKey() );
            }
            catch ( final IndyDataException e )
            {
                throw new IndyWorkflowException( "Failed to lookup store: %s. Reason: %s", e, kl.getKey(),
                                                  e.getMessage() );
            }

            for ( final ContentGenerator generator : contentGenerators )
            {
                generator.handleContentStorage( transferStore, path, txfr, eventMetadata );
            }

            if ( !store.equals( transferStore ) )
            {
                for ( final ContentGenerator generator : contentGenerators )
                {
                    generator.handleContentStorage( transferStore, path, txfr, eventMetadata );
                }
            }
        }

        return txfr;
    }

//    @Override
//    public Transfer store( final List<? extends ArtifactStore> stores, final String path, final InputStream stream,
//                           final TransferOperation op )
//            throws IndyWorkflowException
//    {
//        return store( stores, path, stream, op, new EventMetadata() );
//    }

    @Override
    public Transfer store( final List<? extends ArtifactStore> stores, final StoreKey topKey, final String path, final InputStream stream,
                           final TransferOperation op, final EventMetadata eventMetadata )
            throws IndyWorkflowException
    {
        logger.info( "Storing: {} in: {} with event metadata: {}", path, stores, eventMetadata );
        final Transfer txfr = downloadManager.store( stores, path, stream, op, eventMetadata );
        if ( txfr != null )
        {
            final KeyedLocation kl = (KeyedLocation) txfr.getLocation();
            ArtifactStore transferStore;
            try
            {
                transferStore = storeManager.getArtifactStore( kl.getKey() );
            }
            catch ( final IndyDataException e )
            {
                throw new IndyWorkflowException( "Failed to lookup store: %s. Reason: %s", e, kl.getKey(),
                                                  e.getMessage() );
            }

            for ( final ContentGenerator generator : contentGenerators )
            {
                logger.info( "{} Handling content storage of: {} in: {}", generator, path, transferStore.getKey() );
                generator.handleContentStorage( transferStore, path, txfr, eventMetadata );
            }
        }

        return txfr;
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
        boolean result = false;
        if ( group == store.getKey().getType() )
        {
            List<ArtifactStore> members;
            try
            {
                members = storeManager.getOrderedConcreteStoresInGroup( store.getName(), false );
            }
            catch ( final IndyDataException e )
            {
                throw new IndyWorkflowException( "Failed to lookup concrete members of: %s. Reason: %s", e, store,
                                                  e.getMessage() );
            }

            for ( final ArtifactStore member : members )
            {
                if ( downloadManager.delete( member, path, eventMetadata ) )
                {
                    result = true;
                    for ( final ContentGenerator generator : contentGenerators )
                    {
                        generator.handleContentDeletion( member, path, eventMetadata );
                    }
                }
            }

            if ( result )
            {
                for ( final ContentGenerator generator : contentGenerators )
                {
                    generator.handleContentDeletion( store, path, eventMetadata );
                }
            }
        }
        else
        {
            if ( downloadManager.delete( store, path, eventMetadata ) )
            {
                result = true;
                for ( final ContentGenerator generator : contentGenerators )
                {
                    generator.handleContentDeletion( store, path, eventMetadata );
                }
            }
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
        for ( final ArtifactStore store : stores )
        {
            result = delete( store, path, eventMetadata ) || result;
        }

        return result;
    }

    @Override
    public void rescan( final ArtifactStore store )
            throws IndyWorkflowException
    {
        rescan( store, new EventMetadata() );
    }

    @Override
    public void rescan( final ArtifactStore store, final EventMetadata eventMetadata )
            throws IndyWorkflowException
    {
        downloadManager.rescan( store, eventMetadata );
    }

    @Override
    public void rescanAll( final List<? extends ArtifactStore> stores )
            throws IndyWorkflowException
    {
        rescanAll( stores, new EventMetadata() );
    }

    @Override
    public void rescanAll( final List<? extends ArtifactStore> stores, final EventMetadata eventMetadata )
            throws IndyWorkflowException
    {
        downloadManager.rescanAll( stores, eventMetadata );
    }

    @Override
    public List<StoreResource> list( final ArtifactStore store, final String path )
            throws IndyWorkflowException
    {
        return list( store, path, new EventMetadata() );
    }

    @Override
    public List<StoreResource> list( final ArtifactStore store, final String path, final EventMetadata eventMetadata )
            throws IndyWorkflowException
    {
        List<StoreResource> listed;
        if ( group == store.getKey().getType() )
        {
            List<ArtifactStore> members;
            try
            {
                members = storeManager.getOrderedConcreteStoresInGroup( store.getName(), true );
            }
            catch ( final IndyDataException e )
            {
                throw new IndyWorkflowException( "Failed to lookup concrete members of: %s. Reason: %s", e, store,
                                                  e.getMessage() );
            }

            listed = new ArrayList<StoreResource>();
            for ( final ContentGenerator generator : contentGenerators )
            {
                final List<StoreResource> generated =
                        generator.generateGroupDirectoryContent( (Group) store, members, path, eventMetadata );
                if ( generated != null )
                {
                    listed.addAll( generated );
                }
            }

            for ( final ArtifactStore member : members )
            {
                List<StoreResource> storeListing = null;
                try
                {
                    storeListing = list( member, path, eventMetadata );
                }
                catch ( IndyWorkflowException e )
                {
                    e.filterLocationErrors();
                }

                if ( storeListing != null )
                {
                    listed.addAll( storeListing );
                }
            }
        }
        else
        {
            listed = downloadManager.list( store, path );

            for ( final ContentGenerator producer : contentGenerators )
            {
                final List<StoreResource> produced =
                        producer.generateDirectoryContent( store, path, listed, eventMetadata );
                if ( produced != null )
                {
                    listed.addAll( produced );
                }
            }
        }

        return dedupeListing( listed );
    }

    @Override
    public List<StoreResource> list( final List<? extends ArtifactStore> stores, final String path )
            throws IndyWorkflowException
    {
        final List<StoreResource> listed = new ArrayList<StoreResource>();
        for ( final ArtifactStore store : stores )
        {
            List<StoreResource> storeListing = null;
            try
            {
                storeListing = list( store, path, new EventMetadata() );
            }
            catch ( IndyWorkflowException e )
            {
                e.filterLocationErrors();
            }

            if ( storeListing != null )
            {
                listed.addAll( storeListing );
            }
        }

        return dedupeListing( listed );
    }

    @Override
    public ArtifactData digest( final StoreKey key, final String path, final ContentDigest... types )
            throws IndyWorkflowException
    {
        return contentDigester.digest( key, path, types );
    }

    @Override
    public Transfer getTransfer( final StoreKey store, final String path, final TransferOperation op )
            throws IndyWorkflowException
    {
        try
        {
            return getTransfer( storeManager.getArtifactStore( store ), path, op );
        }
        catch ( final IndyDataException e )
        {
            throw new IndyWorkflowException( ApplicationStatus.BAD_REQUEST.code(),
                                              "Failed to retrieve ArtifactStore for key: %s. Reason: %s", e, store,
                                              e.getMessage() );

        }
    }

    @Override
    public Transfer getTransfer( final ArtifactStore store, final String path, final TransferOperation op )
            throws IndyWorkflowException
    {
        logger.debug( "Getting transfer for: {}/{} (op: {})", store.getKey(), path, op );
        if ( group == store.getKey().getType() )
        {
            KeyedLocation location = LocationUtils.toLocation( store );
            SpecialPathInfo spInfo = specialPathManager.getSpecialPathInfo( location, path );
            if ( spInfo == null || !spInfo.isMergable() )
            {
                try
                {
                    final List<ArtifactStore> allMembers = storeManager.getOrderedConcreteStoresInGroup( store.getName(), true );

                    logger.debug( "Trying to retrieve suitable transfer for: {} in group: {} members:\n{}", path, store.getName(), allMembers );

                    return getTransfer( allMembers, path, op );
                }
                catch ( final IndyDataException e )
                {
                    throw new IndyWorkflowException( "Failed to lookup concrete members of: %s. Reason: %s", e, store,
                                                      e.getMessage() );
                }
            }
            else
            {
                logger.debug( "Detected mergable special path: {}/{}.", store.getKey(), path );
            }
        }

        logger.debug( "Retrieving storage reference (Transfer) directly for: {}/{}", store.getKey(), path );
        return downloadManager.getStorageReference( store, path, op );
    }

    @Override
    public Transfer getTransfer( final List<ArtifactStore> stores, final String path, final TransferOperation op )
            throws IndyWorkflowException
    {
        Logger logger = LoggerFactory.getLogger( getClass() );
        logger.debug( "Looking for path: '{}' in stores: {}", path,
                      stores.stream().map( ( store ) -> store.getKey() ).collect( Collectors.toList() ) );

        return downloadManager.getStorageReference( stores, path, op );
    }

    @Override
    public HttpExchangeMetadata getHttpMetadata( final Transfer txfr )
            throws IndyWorkflowException
    {
        final Transfer meta = txfr.getSiblingMeta( HttpExchangeMetadata.FILE_EXTENSION );
        return readExchangeMetadata( meta );
    }

    @Override
    public HttpExchangeMetadata getHttpMetadata( final StoreKey key, final String path )
            throws IndyWorkflowException
    {
        Transfer transfer = getTransfer( key, path, TransferOperation.DOWNLOAD );
        if ( transfer != null && transfer.exists() )
        {
            Transfer meta = transfer.getSiblingMeta( HttpExchangeMetadata.FILE_EXTENSION );
            if ( meta != null && meta.exists() )
            {
                return readExchangeMetadata( meta );
            }
        }

        return null;
    }

    @Override
    // TODO: to add content generation handling here, for things like merged metadata, checksum files, etc.
    public boolean exists(ArtifactStore store, String path)
        throws IndyWorkflowException
    {
        if ( !checkMask(store, path))
        {
            return false;
        }

        Logger logger = LoggerFactory.getLogger( getClass() );
        logger.debug( "Checking existence of: {} in: {}", path, store.getKey() );
        if ( store instanceof Group )
        {
            try
            {
                final List<ArtifactStore> allMembers = storeManager.getOrderedConcreteStoresInGroup( store.getName(), true );

                logger.debug( "Trying to retrieve suitable transfer for: {} in group: {} members:\n{}", path, allMembers, store.getName() );
                for ( ArtifactStore member : allMembers )
                {
                    if ( exists( member, path ) )
                    {
                        return true;
                    }
                }

                return false;
            }
            catch ( final IndyDataException e )
            {
                throw new IndyWorkflowException( "Failed to lookup concrete members of: %s. Reason: %s", e, store,
                                                 e.getMessage() );
            }
        }
        else
        {
            return downloadManager.exists(store, path);
        }
    }

    private HttpExchangeMetadata readExchangeMetadata( final Transfer meta )
            throws IndyWorkflowException
    {
        logger.trace( "Reading HTTP exchange metadata from: {}", meta );
        if ( meta != null && meta.exists() )
        {
            try(InputStream stream = meta.openInputStream( false ))
            {
                String raw = IOUtils.toString( stream );
                logger.debug( "HTTP Metadata string is:\n\n{}\n\n", raw );

                return mapper.readValue( raw, HttpExchangeMetadata.class );
            }
            catch ( final IOException e )
            {
                throw new IndyWorkflowException( "HTTP exchange metadata appears to be damaged: %s. Reason: %s", e,
                                                  meta, e.getMessage() );
            }
        }
        else
        {
            logger.trace( "Cannot read HTTP exchange: {}. Transfer is missing!", meta );
        }

        return null;
    }

}
