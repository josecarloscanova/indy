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
package org.commonjava.indy.koji.conf;

import com.redhat.red.build.koji.config.KojiConfig;
import org.commonjava.indy.conf.AbstractIndyMapConfig;
import org.commonjava.indy.conf.IndyConfigInfo;
import org.commonjava.util.jhttpc.model.SiteConfig;
import org.commonjava.util.jhttpc.model.SiteConfigBuilder;
import org.commonjava.util.jhttpc.model.SiteTrustType;
import org.commonjava.web.config.ConfigurationException;
import org.commonjava.web.config.annotation.SectionName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.apache.commons.io.FileUtils.readFileToString;
import static org.commonjava.util.jhttpc.model.SiteConfig.DEFAULT_MAX_CONNECTIONS;
import static org.commonjava.util.jhttpc.model.SiteConfig.DEFAULT_PROXY_PORT;
import static org.commonjava.util.jhttpc.model.SiteConfig.DEFAULT_REQUEST_TIMEOUT_SECONDS;

/**
 * Created by jdcasey on 5/20/16.
 */
//@SectionName( IndyKojiConfig.SECTION_NAME )
@ApplicationScoped
@Named( IndyKojiConfig.SECTION_NAME)
public class IndyKojiConfig
        extends AbstractIndyMapConfig
//        implements KojiConfig
{
    private static final String KOJI_SITE_ID = "koji";

    private static final String DEFAULT_CONFIG_FILE_NAME = "default-koji.conf";

    public static final String SECTION_NAME = "koji";

    private static final String TARGET_KEY_PREFIX = "target.";

    private static final boolean DEFAULT_ENABLED = false;

    private Boolean enabled;

    private String url;

    private String clientPemPath;

    private String serverPemPath;

    private String keyPassword;

    private Integer maxConnections;

    private String proxyHost;

    private Integer proxyPort;

    private String proxyUser;

    private Integer requestTimeoutSeconds;

    private String siteTrustType;

    private String proxyPassword;

    private String storageRootUrl;

    private List<String> tagPatterns;

    private Map<String, String> targetGroups;

    public IndyKojiConfig()
    {
        super( SECTION_NAME );

        Logger logger = LoggerFactory.getLogger( getClass() );
        logger.info( "Starting Indy Koji configuration instance..." );
    }

    @PostConstruct
    public void running()
    {
        Logger logger = LoggerFactory.getLogger( getClass() );
        logger.info( "PostConstruct" );
    }

    public KojiConfig getKojiConfig()
    {
        return new KojiConfig(){

            @Override
            public SiteConfig getKojiSiteConfig()
                    throws IOException
            {
                return new SiteConfigBuilder().withId( getKojiSiteId() )
                                              .withKeyCertPem( getClientPemContent() )
                                              .withServerCertPem( getServerPemContent() )
                                              .withUri( getKojiURL() )
                                              .withMaxConnections( getMaxConnections() )
                                              .withProxyHost( getProxyHost() )
                                              .withProxyPort( getProxyPort() )
                                              .withProxyUser( getProxyUser() )
                                              .withRequestTimeoutSeconds( getRequestTimeoutSeconds() )
                                              .withTrustType( SiteTrustType.getType( getSiteTrustType() ) )
                                              .build();
            }

            @Override
            public String getKojiURL()
            {
                return getUrl();
            }

            @Override
            public String getKojiClientCertificatePassword()
            {
                return keyPassword;
            }

            @Override
            public String getKojiSiteId()
            {
                return KOJI_SITE_ID;
            }

        };
    }


    public String getServerPemContent()
            throws IOException
    {
        return readPemContent( getServerPemPath() );
    }

    public String getClientPemContent()
            throws IOException
    {
        return readPemContent( getClientPemPath() );
    }

    private String readPemContent( String pemPath )
            throws IOException
    {
        Logger logger = LoggerFactory.getLogger( getClass() );
        logger.trace( "Reading PEM content from path: '{}'", pemPath );

        if ( pemPath == null )
        {
            return null;
        }

        File f = new File( pemPath );
        if ( !f.exists() || f.isDirectory() )
        {
            return null;
        }

        String pem =  readFileToString( f );

        logger.trace( "Got PEM content:\n\n{}\n\n", pem );

        return pem;
    }

    public Integer getMaxConnections()
    {
        return maxConnections == null ? DEFAULT_MAX_CONNECTIONS : maxConnections;
    }

    public String getServerPemPath()
    {
        return serverPemPath;
    }

    public String getClientPemPath()
    {
        return clientPemPath;
    }

    public String getProxyHost()
    {
        return proxyHost;
    }

    public Integer getProxyPort()
    {
        return proxyPort == null ? DEFAULT_PROXY_PORT : proxyPort;
    }

    public String getProxyUser()
    {
        return proxyUser;
    }

    public Integer getRequestTimeoutSeconds()
    {
        return requestTimeoutSeconds == null ? DEFAULT_REQUEST_TIMEOUT_SECONDS : requestTimeoutSeconds;
    }

    public String getSiteTrustType()
    {
        return siteTrustType;
    }

    public String getUrl()
    {
        return url;
    }

    public String getKeyPassword()
    {
        return keyPassword;
    }

    public String getProxyPassword()
    {
        return proxyPassword;
    }

    public String getStorageRootUrl()
    {
        return storageRootUrl;
    }

    public void setUrl( String url )
    {
        this.url = url;
    }

    public void setClientPemPath( String clientPemPath )
    {
        this.clientPemPath = clientPemPath;
    }

    public void setServerPemPath( String serverPemPath )
    {
        this.serverPemPath = serverPemPath;
    }

    public void setKeyPassword( String keyPassword )
    {
        this.keyPassword = keyPassword;
    }

    public void setMaxConnections( Integer maxConnections )
    {
        this.maxConnections = maxConnections;
    }

    public void setProxyHost( String proxyHost )
    {
        this.proxyHost = proxyHost;
    }

    public void setProxyPort( Integer proxyPort )
    {
        this.proxyPort = proxyPort;
    }

    public void setProxyUser( String proxyUser )
    {
        this.proxyUser = proxyUser;
    }

    public void setRequestTimeoutSeconds( Integer requestTimeoutSeconds )
    {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public void setSiteTrustType( String siteTrustType )
    {
        this.siteTrustType = siteTrustType;
    }

    public void setProxyPassword( String proxyPassword )
    {
        this.proxyPassword = proxyPassword;
    }

    public void setStorageRootUrl( String storageRootUrl )
    {
        this.storageRootUrl = storageRootUrl;
    }

    public List<String> getTagPatterns()
    {
        return tagPatterns;
    }

    public void setTagPatterns( List<String> tagPatterns )
    {
        this.tagPatterns = tagPatterns;
    }

    public Map<String, String> getTargetGroups()
    {
        return targetGroups;
    }

    public void setTargetGroups( Map<String, String> targetGroups )
    {
        this.targetGroups = targetGroups;
    }

    public Boolean getEnabled()
    {
        return enabled == null ? DEFAULT_ENABLED : enabled;
    }

    public void setEnabled( Boolean enabled )
    {
        this.enabled = enabled;
    }

    public boolean isEnabled()
    {
        return getEnabled();
    }

    public boolean isTagAllowed( String name )
    {
        Optional<String> result = tagPatterns.stream().filter( ( pattern ) -> name.matches( pattern ) ).findFirst();

        return result.isPresent();
    }

    public void parameter( final String name, final String value )
            throws ConfigurationException
    {
        Logger logger = LoggerFactory.getLogger( getClass() );
        logger.trace( "\n\nGot koji config parameter: '{}' with value: '{}'\n\n", name, value );
        switch ( name )
        {
            case "enabled":
            {
                this.enabled = Boolean.valueOf( value );
                break;
            }
            case "tag.pattern":
            {
                if ( tagPatterns == null )
                {
                    tagPatterns = new ArrayList<>();
                }

                this.tagPatterns.add( value );
                break;
            }
            case "storage.root.url":
            {
                this.storageRootUrl = value;
                break;
            }
            case "proxy.password":
            {
                this.proxyPassword = value;
                break;
            }
            case "proxy.user":
            {
                this.proxyUser = value;
                break;
            }
            case "proxy.host":
            {
                this.proxyHost = value;
                break;
            }
            case "proxy.port":
            {
                this.proxyPort = Integer.valueOf( value );
                break;
            }
            case "client.pem.password":
            {
                this.keyPassword = value;
                break;
            }
            case "url":
            {
                this.url = value;
                break;
            }
            case "ssl.trust.type":
            {
                this.siteTrustType = value;
                break;
            }
            case "request.timeout.seconds":
            {
                this.requestTimeoutSeconds = Integer.valueOf( value );
                break;
            }
            case "client.pem.path":
            {
                this.clientPemPath = value;
                break;
            }
            case "server.pem.path":
            {
                this.serverPemPath = value;
                break;
            }
            case "max.connections":
            {
                this.maxConnections = Integer.valueOf( value );
                break;
            }
            default:
            {
                if ( name.startsWith( TARGET_KEY_PREFIX ) && name.length() > TARGET_KEY_PREFIX.length() )
                {
                    if ( targetGroups == null )
                    {
                        targetGroups = new LinkedHashMap<>();
                    }

                    String source = name.substring( "target.".length(), name.length() - 1 );
                    targetGroups.put( source, value );
                }
                else
                {
                    throw new ConfigurationException(
                            "Invalid parameter: '%s'.",
                            value, name, SECTION_NAME );
                }
            }
        }
    }

    @Override
    public void sectionStarted( final String name )
            throws ConfigurationException
    {
        // NOP; just block map init in the underlying implementation.
    }

    public String getDefaultConfigFileName()
    {
        return new File( IndyConfigInfo.CONF_INCLUDES_DIR, DEFAULT_CONFIG_FILE_NAME ).getPath();
    }

    public InputStream getDefaultConfig()
    {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream( DEFAULT_CONFIG_FILE_NAME );
    }

    public String getTargetGroup( String name )
    {
        if ( targetGroups == null )
        {
            return null;
        }

        for ( String key : targetGroups.keySet() )
        {
            if ( name.matches( key ) )
            {
                return targetGroups.get( key );
            }
        }

        return null;
    }

}