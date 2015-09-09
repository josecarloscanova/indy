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
package org.commonjava.aprox.folo.change;

import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.commonjava.aprox.folo.ctl.FoloConstants;
import org.commonjava.aprox.folo.data.FoloContentException;
import org.commonjava.aprox.folo.data.FoloRecordManager;
import org.commonjava.aprox.folo.model.StoreEffect;
import org.commonjava.aprox.folo.model.TrackingKey;
import org.commonjava.aprox.model.galley.KeyedLocation;
import org.commonjava.maven.galley.event.FileAccessEvent;
import org.commonjava.maven.galley.event.FileStorageEvent;
import org.commonjava.maven.galley.model.Location;
import org.commonjava.maven.galley.model.Transfer;
import org.commonjava.maven.galley.model.TransferOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FoloTrackingListener
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    private FoloRecordManager recordManager;

    public void onFileAccess( @Observes final FileAccessEvent event )
    {
        final TrackingKey trackingKey = (TrackingKey) event.getEventMetadata()
                                                           .get( FoloConstants.TRACKING_KEY );
        if ( trackingKey == null )
        {
            logger.info( "No tracking key for access to: {}", event.getTransfer() );
            return;
        }

        final Transfer transfer = event.getTransfer();
        if ( transfer == null )
        {
            logger.info( "No transfer: {}", event );
            return;
        }

        final Location location = transfer.getLocation();
        if ( !( location instanceof KeyedLocation ) )
        {
            logger.info( "Not in a keyed location: {}", event.getTransfer() );
            return;
        }

        try
        {
            final KeyedLocation keyedLocation = (KeyedLocation) location;
            logger.debug( "Tracking report: {} += {} in {} (DOWNLOAD)", trackingKey, transfer.getPath(),
                          keyedLocation.getKey() );

            recordManager.recordArtifact( trackingKey, keyedLocation.getKey(), transfer.getPath(), StoreEffect.DOWNLOAD );
        }
        catch ( final FoloContentException e )
        {
            logger.error( String.format( "Failed to record download: %s. Reason: %s", transfer, e.getMessage() ), e );
        }
    }

    public void onFileUpload( @Observes final FileStorageEvent event )
    {
        final TrackingKey trackingKey = (TrackingKey) event.getEventMetadata()
                                                           .get( FoloConstants.TRACKING_KEY );
        if ( trackingKey == null )
        {
            logger.info( "No tracking key. Not recording." );
            return;
        }

        final Transfer transfer = event.getTransfer();
        if ( transfer == null )
        {
            logger.info( "No transfer. Not recording." );
            return;
        }

        final Location location = transfer.getLocation();
        if ( !( location instanceof KeyedLocation ) )
        {
            logger.info( "Invalid transfer source location: {}. Not recording.", location );
            return;
        }

        final TransferOperation op = event.getType();
        StoreEffect effect = null;
        switch ( op )
        {
            case DOWNLOAD:
            {
                effect = StoreEffect.DOWNLOAD;
                break;
            }
            case UPLOAD:
            {
                effect = StoreEffect.UPLOAD;
                break;
            }
            default:
            {
                logger.debug( "Ignoring transfer operation: {} for: {}", op, transfer );
                return;
            }
        }

        try
        {
            final KeyedLocation keyedLocation = (KeyedLocation) location;
            logger.debug( "Tracking report: {} += {} in {} ({})", trackingKey, transfer.getPath(),
                          keyedLocation.getKey(), effect );

            recordManager.recordArtifact( trackingKey, keyedLocation.getKey(), transfer.getPath(), effect );
        }
        catch ( final FoloContentException e )
        {
            logger.error( String.format( "Failed to record download: %s. Reason: %s", transfer, e.getMessage() ), e );
        }
    }

}