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
package org.commonjava.indy.flat.data;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.commonjava.indy.action.MigrationAction;
import org.commonjava.indy.audit.ChangeSummary;
import org.commonjava.indy.data.IndyDataException;
import org.commonjava.indy.data.StoreDataManager;
import org.commonjava.indy.model.core.HostedRepository;
import org.commonjava.indy.model.core.RemoteRepository;
import org.commonjava.indy.model.core.StoreType;
import org.commonjava.indy.subsys.datafile.DataFile;
import org.commonjava.maven.galley.event.EventMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named( "legacy-storedb-migration" )
public class LegacyDataMigrationAction
    implements MigrationAction
{

    private static final String LEGACY_HOSTED_REPO_PREFIX = "deploy_point";

    private static final String LEGACY_REMOTE_REPO_PREFIX = "repository";

    public static final String LEGACY_MIGRATION = "legacy-migration";

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    private StoreDataManager data;

    @Override
    public String getId()
    {
        return "Legacy store-definition data migrator";
    }

    @Override
    public boolean migrate()
    {
        if ( !( data instanceof DataFileStoreDataManager ) )
        {
            return true;
        }

        final DataFileStoreDataManager data = (DataFileStoreDataManager) this.data;

        final DataFile basedir = data.getFileManager()
                                     .getDataFile( DataFileStoreDataManager.INDY_STORE );
        final ChangeSummary summary =
            new ChangeSummary( ChangeSummary.SYSTEM_USER, "Migrating legacy store definitions." );

        if ( !basedir.exists() )
        {
            return false;
        }

        final String[] dirs = basedir.list();
        if ( dirs == null || dirs.length < 1 )
        {
            return false;
        }

        boolean changed = false;
        for ( final String name : dirs )
        {
            final DataFile dir = basedir.getChild( name );
            String newName = null;
            if ( name.startsWith( LEGACY_HOSTED_REPO_PREFIX ) )
            {
                newName = StoreType.hosted.singularEndpointName() + name.substring( LEGACY_HOSTED_REPO_PREFIX.length() );
            }
            else if ( name.startsWith( LEGACY_REMOTE_REPO_PREFIX ) )
            {
                newName = StoreType.remote.singularEndpointName() + name.substring( LEGACY_REMOTE_REPO_PREFIX.length() );
            }

            if ( newName != null )
            {
                logger.info( "Migrating storage: '{}' to '{}'", name, newName );

                final DataFile newDir = basedir.getChild( newName );
                dir.renameTo( newDir, summary );

                changed = true;
            }
        }

        try
        {
            data.reload();

            final List<HostedRepository> hosted = data.getAllHostedRepositories();
            for ( final HostedRepository repo : hosted )
            {
                data.storeArtifactStore( repo, summary, false, true,
                                         new EventMetadata().set( StoreDataManager.EVENT_ORIGIN, LEGACY_MIGRATION ) );
            }

            final List<RemoteRepository> remotes = data.getAllRemoteRepositories();
            for ( final RemoteRepository repo : remotes )
            {
                data.storeArtifactStore( repo, summary, false, true,
                                         new EventMetadata().set( StoreDataManager.EVENT_ORIGIN, LEGACY_MIGRATION ) );
            }

            data.reload();
        }
        catch ( final IndyDataException e )
        {
            throw new RuntimeException( "Failed to reload artifact-store definitions: " + e.getMessage(), e );
        }

        return changed;
    }

    @Override
    public int getMigrationPriority()
    {
        return 85;
    }

}
