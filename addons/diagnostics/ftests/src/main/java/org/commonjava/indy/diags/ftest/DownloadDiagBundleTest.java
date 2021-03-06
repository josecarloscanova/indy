package org.commonjava.indy.diags.ftest;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.commonjava.indy.client.core.Indy;
import org.commonjava.indy.client.core.IndyClientException;
import org.commonjava.indy.client.core.IndyClientModule;
import org.commonjava.indy.client.core.module.IndyRawHttpModule;
import org.commonjava.indy.diag.client.IndyDiagnosticsClientModule;
import org.commonjava.indy.diag.data.DiagnosticsManager;
import org.commonjava.indy.ftest.core.AbstractIndyFunctionalTest;
import org.commonjava.indy.model.core.io.IndyObjectMapper;
import org.commonjava.util.jhttpc.auth.MemoryPasswordManager;
import org.commonjava.util.jhttpc.model.SiteConfig;
import org.commonjava.util.jhttpc.model.SiteConfigBuilder;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.commonjava.indy.client.core.util.UrlUtils.buildUrl;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * Created by jdcasey on 1/11/17.
 *
 * Download the diagnostic bundle zip from Indy.
 */
public class DownloadDiagBundleTest
        extends AbstractIndyFunctionalTest
{

    private IndyDiagnosticsClientModule module = new IndyDiagnosticsClientModule();

    @Test
    @Ignore
    public void run()
            throws IndyClientException, IOException
    {
        boolean foundThreadDump = false;
        int logCount = 0;
        try (ZipInputStream in = module.getDiagnosticBundle())
        {
            ZipEntry entry = null;
            while( ( entry = in.getNextEntry() ) != null )
            {
                if ( entry.getName().equals( DiagnosticsManager.THREAD_DUMP_FILE ) )
                {
                    logger.debug( "\n\nGot thread dump:\n\n{}\n\n", IOUtils.toString( in ) );
                    foundThreadDump = true;
                }
                else if ( entry.getName().startsWith( DiagnosticsManager.LOGS_DIR+ "/" ) )
                {
                    logger.debug( "\n\nGot log file: '{}'\n\n", entry.getName() );
                    logCount++;
                }
                else
                {
                    logger.debug( "\n\nGot unknown file: '{}'\n\n", entry.getName() );
                }

            }
        }

        assertThat( "Didn't find thread dump!", foundThreadDump, equalTo( true ) );
        assertThat( "Didn't find any logs!", logCount > 0, equalTo( true ) );
    }

    protected Indy createIndyClient()
            throws IndyClientException
    {
        SiteConfig config = new SiteConfigBuilder( "indy", fixture.getUrl() ).withRequestTimeoutSeconds( 120 ).build();
        Collection<IndyClientModule> modules = getAdditionalClientModules();

        return new Indy( config, new MemoryPasswordManager(), new IndyObjectMapper( getAdditionalMapperModules() ),
                         modules.toArray(new IndyClientModule[modules.size()]) );
    }

    @Override
    protected Collection<IndyClientModule> getAdditionalClientModules()
    {
        return Collections.singleton( module );
    }
}
