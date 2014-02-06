/*******************************************************************************
 * Copyright (C) 2014 John Casey.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.commonjava.aprox.bind.vertx.boot;

import java.net.URL;
import java.util.Properties;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.Configurator;
import org.apache.log4j.spi.LoggerRepository;
import org.commonjava.aprox.conf.AproxConfigFactory;
import org.commonjava.util.logging.Log4jUtil;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.platform.Verticle;

public class Booter
    extends Verticle
{
    public static void main( final String[] args )
    {
        // FIXME Make this configurable via BootOptions.
        configureLogging();

        final BootOptions boot = new BootOptions();
        final CmdLineParser parser = new CmdLineParser( boot );
        boolean canStart = true;
        try
        {
            parser.parseArgument( args );
        }
        catch ( final CmdLineException e )
        {
            System.out.printf( "ERROR: %s", e.getMessage() );
            printUsage( parser, e );
        }

        if ( boot.isHelp() )
        {
            printUsage( parser, null );
            canStart = false;
        }

        if ( canStart )
        {
            new Booter( boot ).run();
        }
    }

    private static void configureLogging()
    {
        Log4jUtil.configure( Level.WARN );

        final Configurator log4jConfigurator = new Configurator()
        {
            @Override
            public void doConfigure( final URL notUsed, final LoggerRepository repo )
            {
                final Layout layout = new PatternLayout( "%d [%t] %-5p %c - %m%n" );
                final ConsoleAppender cAppender = new ConsoleAppender( layout );
                cAppender.setThreshold( Level.ALL );

                final Logger logger = repo.getLogger( "org.commonjava" );
                logger.removeAllAppenders();
                logger.addAppender( cAppender );
                logger.setLevel( Level.INFO );
            }
        };

        log4jConfigurator.doConfigure( null, LogManager.getLoggerRepository() );
    }

    public static void printUsage( final CmdLineParser parser, final CmdLineException error )
    {
        if ( error != null )
        {
            System.err.println( "Invalid option(s): " + error.getMessage() );
            System.err.println();
        }

        System.err.println( "Usage: $0 [OPTIONS] [<target-path>]" );
        System.err.println();
        System.err.println();
        // If we are running under a Linux shell COLUMNS might be available for the width
        // of the terminal.
        parser.setUsageWidth( ( System.getenv( "COLUMNS" ) == null ? 100 : Integer.valueOf( System.getenv( "COLUMNS" ) ) ) );
        parser.printUsage( System.err );
        System.err.println();
    }

    private final BootOptions bootOptions;

    public Booter( final BootOptions bootOptions )
    {
        this.bootOptions = bootOptions;
    }

    public void run()
    {
        if ( bootOptions.getConfig() != null )
        {
            final Properties properties = System.getProperties();
            properties.setProperty( AproxConfigFactory.CONFIG_PATH_PROP, bootOptions.getConfig() );
            System.setProperties( properties );
        }

        final Weld weld = new Weld();
        final WeldContainer container = weld.initialize();

        final MasterRouter router = container.instance()
                                             .select( MasterRouter.class )
                                             .get();
        //        router.initializeComponents();

        setVertx( new DefaultVertx() );

        vertx.createHttpServer()
             .requestHandler( router )
             .listen( bootOptions.getPort(), bootOptions.getBind() );

        System.out.printf( "\n\nAProx listening on %s:%d\n\n", bootOptions.getBind(), bootOptions.getPort() );

        synchronized ( this )
        {
            try
            {
                wait();
            }
            catch ( final InterruptedException e )
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

}