package org.commonjava.aprox.infinispan.data;

import static org.commonjava.aprox.model.StoreType.deploy_point;
import static org.commonjava.aprox.model.StoreType.group;
import static org.commonjava.aprox.model.StoreType.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.commonjava.aprox.data.ProxyDataException;
import org.commonjava.aprox.data.StoreDataManager;
import org.commonjava.aprox.model.ArtifactStore;
import org.commonjava.aprox.model.DeployPoint;
import org.commonjava.aprox.model.Group;
import org.commonjava.aprox.model.Repository;
import org.commonjava.aprox.model.StoreKey;
import org.commonjava.aprox.model.StoreType;
import org.infinispan.Cache;
import org.infinispan.manager.CacheContainer;

public class InfinispanDataManager
    implements StoreDataManager
{

    @Inject
    private CacheContainer container;

    private Cache<StoreKey, ArtifactStore> storeCache;

    @PostConstruct
    public void injectCaches()
    {
        storeCache = container.getCache( "aprox-data" );
        storeCache.start();
    }

    public InfinispanDataManager()
    {
    }

    public InfinispanDataManager( final Cache<StoreKey, ArtifactStore> storeCache )
    {
        this.storeCache = storeCache;
    }

    @Override
    public void storeDeployPoints( final Collection<DeployPoint> deploys )
        throws ProxyDataException
    {
        store( false, deploys.toArray( new ArtifactStore[] {} ) );
    }

    @Override
    public boolean storeDeployPoint( final DeployPoint deploy )
        throws ProxyDataException
    {
        return store( false, deploy );
    }

    @Override
    public boolean storeDeployPoint( final DeployPoint deploy, final boolean skipIfExists )
        throws ProxyDataException
    {
        return store( skipIfExists, deploy );
    }

    @Override
    public void storeRepositories( final Collection<Repository> repos )
        throws ProxyDataException
    {
        store( false, repos.toArray( new ArtifactStore[] {} ) );
    }

    @Override
    public boolean storeRepository( final Repository proxy )
        throws ProxyDataException
    {
        return store( false, proxy );
    }

    @Override
    public boolean storeRepository( final Repository repository, final boolean skipIfExists )
        throws ProxyDataException
    {
        return store( skipIfExists, repository );
    }

    @Override
    public void storeGroups( final Collection<Group> groups )
        throws ProxyDataException
    {
        store( false, groups.toArray( new ArtifactStore[] {} ) );
    }

    @Override
    public boolean storeGroup( final Group group )
        throws ProxyDataException
    {
        return store( false, group );
    }

    @Override
    public boolean storeGroup( final Group group, final boolean skipIfExists )
        throws ProxyDataException
    {
        return store( skipIfExists, group );
    }

    @Override
    public boolean storeArtifactStore( final ArtifactStore store )
        throws ProxyDataException
    {
        return store( false, store );
    }

    @Override
    public boolean storeArtifactStore( final ArtifactStore store, final boolean skipIfExists )
        throws ProxyDataException
    {
        final boolean result = store( skipIfExists, store );

        return result;
    }

    @Override
    public void deleteDeployPoint( final DeployPoint deploy )
        throws ProxyDataException
    {
        delete( deploy );
    }

    @Override
    public void deleteDeployPoint( final String name )
        throws ProxyDataException
    {
        delete( StoreType.deploy_point, name );
    }

    @Override
    public void deleteRepository( final Repository repo )
        throws ProxyDataException
    {
        delete( repo );
    }

    @Override
    public void deleteRepository( final String name )
        throws ProxyDataException
    {
        delete( StoreType.repository, name );
    }

    @Override
    public void deleteGroup( final Group group )
        throws ProxyDataException
    {
        delete( group );
    }

    @Override
    public void deleteGroup( final String name )
        throws ProxyDataException
    {
        delete( StoreType.group, name );
    }

    @Override
    public void deleteArtifactStore( final StoreKey key )
        throws ProxyDataException
    {
        delete( key.getType(), key.getName() );
    }

    @Override
    public DeployPoint getDeployPoint( final String name )
        throws ProxyDataException
    {
        return get( deploy_point, name, DeployPoint.class );
    }

    @Override
    public Repository getRepository( final String name )
        throws ProxyDataException
    {
        return get( repository, name, Repository.class );
    }

    @Override
    public Group getGroup( final String name )
        throws ProxyDataException
    {
        return get( group, name, Group.class );
    }

    @Override
    public List<Group> getAllGroups()
        throws ProxyDataException
    {
        return getAllOfType( group, Group.class );
    }

    @Override
    public List<Repository> getAllRepositories()
        throws ProxyDataException
    {
        return getAllOfType( repository, Repository.class );
    }

    @Override
    public List<DeployPoint> getAllDeployPoints()
        throws ProxyDataException
    {
        return getAllOfType( deploy_point, DeployPoint.class );
    }

    @Override
    public List<ArtifactStore> getOrderedConcreteStoresInGroup( final String groupName )
        throws ProxyDataException
    {
        return getGroupOrdering( groupName, false );
    }

    @Override
    public List<ArtifactStore> getOrderedStoresInGroup( final String groupName )
        throws ProxyDataException
    {
        return getGroupOrdering( groupName, true );
    }

    private List<ArtifactStore> getGroupOrdering( final String groupName, final boolean includeGroups )
        throws ProxyDataException
    {
        final Group master = get( group, groupName, Group.class );
        if ( master == null )
        {
            return Collections.emptyList();
        }

        final List<ArtifactStore> result = new ArrayList<ArtifactStore>();
        recurseGroup( master, result, includeGroups );

        return result;
    }

    @Override
    public Set<Group> getGroupsContaining( final StoreKey repo )
        throws ProxyDataException
    {
        final Set<Group> result = new HashSet<Group>();
        for ( final Group g : getAllOfType( group, Group.class ) )
        {
            if ( groupContains( g, repo ) )
            {
                result.add( g );
            }
        }

        return result;
    }

    @Override
    public void clear()
        throws ProxyDataException
    {
        storeCache.clear();
    }

    @Override
    public void install()
        throws ProxyDataException
    {
        // NOP.
    }

    private boolean store( final boolean skipIfExists, final ArtifactStore... stores )
        throws ProxyDataException
    {
        boolean result = true;

        for ( final ArtifactStore store : stores )
        {
            if ( skipIfExists )
            {
                storeCache.putIfAbsent( store.getKey(), store );
                if ( !storeCache.containsKey( store.getKey() ) )
                {
                    result = false;
                }
            }
            else
            {
                storeCache.put( store.getKey(), store );
            }
        }

        return result;
    }

    private void delete( final ArtifactStore... stores )
    {
        for ( final ArtifactStore store : stores )
        {
            storeCache.remove( store.getKey() );
        }
    }

    private void delete( final StoreType type, final String name )
    {
        storeCache.remove( new StoreKey( type, name ) );
    }

    private <T extends ArtifactStore> T get( final StoreType type, final String name, final Class<T> klass )
    {
        return klass.cast( storeCache.get( new StoreKey( type, name ) ) );
    }

    private <T extends ArtifactStore> T get( final StoreKey key, final Class<T> klass )
    {
        return klass.cast( storeCache.get( key ) );
    }

    private ArtifactStore get( final StoreKey key )
    {
        return storeCache.get( key );
    }

    private <T extends ArtifactStore> List<T> getAllOfType( final StoreType type, final Class<T> klass )
    {
        final List<T> result = new ArrayList<T>();
        for ( final Entry<StoreKey, ArtifactStore> entry : storeCache.entrySet() )
        {
            if ( entry.getKey()
                      .getType() == type )
            {
                result.add( klass.cast( entry.getValue() ) );
            }
        }

        return result;
    }

    private void recurseGroup( final Group master, final List<ArtifactStore> result, final boolean includeGroups )
    {
        if ( master == null )
        {
            return;
        }

        if ( includeGroups )
        {
            result.add( master );
        }

        for ( final StoreKey key : master.getConstituents() )
        {
            final StoreType type = key.getType();
            if ( type == StoreType.group )
            {
                recurseGroup( get( key, Group.class ), result, includeGroups );
            }
            else
            {
                final ArtifactStore store = get( key );
                if ( store != null )
                {
                    result.add( store );
                }
            }
        }
    }

    private boolean groupContains( final Group g, final StoreKey key )
    {
        if ( g == null )
        {
            return false;
        }

        if ( g.getConstituents()
              .contains( key ) )
        {
            return true;
        }
        else
        {
            for ( final StoreKey constituent : g.getConstituents() )
            {
                if ( constituent.getType() == group )
                {
                    final Group embedded = get( constituent, Group.class );
                    if ( embedded != null && groupContains( embedded, key ) )
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public ArtifactStore getArtifactStore( final StoreKey key )
        throws ProxyDataException
    {
        return storeCache.get( key );
    }

    @Override
    public List<ArtifactStore> getAllArtifactStores()
        throws ProxyDataException
    {
        return new ArrayList<ArtifactStore>( storeCache.values() );
    }

    @Override
    public List<? extends ArtifactStore> getAllArtifactStores( final StoreType type )
        throws ProxyDataException
    {
        return getAllOfType( type, type.getStoreClass() );
    }

    @Override
    public boolean hasRepository( final String name )
    {
        return hasArtifactStore( new StoreKey( StoreType.repository, name ) );
    }

    @Override
    public boolean hasGroup( final String name )
    {
        return hasArtifactStore( new StoreKey( StoreType.group, name ) );
    }

    @Override
    public boolean hasDeployPoint( final String name )
    {
        return hasArtifactStore( new StoreKey( StoreType.deploy_point, name ) );
    }

    @Override
    public boolean hasArtifactStore( final StoreKey key )
    {
        return storeCache.containsKey( key );
    }

}