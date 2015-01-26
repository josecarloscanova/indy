package org.commonjava.aprox.promote.ftest;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.Set;

import org.commonjava.aprox.promote.client.AproxPromoteClientModule;
import org.commonjava.aprox.promote.model.PromoteRequest;
import org.commonjava.aprox.promote.model.PromoteResult;
import org.junit.Test;

public class PromotionManager5Test
    extends AbstractPromotionManagerTest
{

    @Test
    public void rollback_PurgeSource_PushTwoArtifactsToHostedRepo_PromoteSuccessThenRollback_VerifyContentInSource()
        throws Exception
    {
        PromoteResult result =
            client.module( AproxPromoteClientModule.class )
                  .promote( new PromoteRequest( source.getKey(), target.getKey() ).setPurgeSource( true ) );

        assertThat( result.getRequest()
                          .getSource(), equalTo( source.getKey() ) );
        assertThat( result.getRequest()
                          .getTarget(), equalTo( target.getKey() ) );

        Set<String> pending = result.getPendingPaths();
        assertThat( pending == null || pending.isEmpty(), equalTo( true ) );

        Set<String> completed = result.getCompletedPaths();
        assertThat( completed, notNullValue() );
        assertThat( completed.size(), equalTo( 2 ) );

        assertThat( result.getError(), nullValue() );

        result = client.module( AproxPromoteClientModule.class )
                       .rollback( result );

        assertThat( result.getRequest()
                          .getSource(), equalTo( source.getKey() ) );
        assertThat( result.getRequest()
                          .getTarget(), equalTo( target.getKey() ) );

        completed = result.getCompletedPaths();
        assertThat( completed == null || completed.isEmpty(), equalTo( true ) );

        pending = result.getPendingPaths();
        assertThat( pending, notNullValue() );
        assertThat( pending.size(), equalTo( 2 ) );

        assertThat( result.getError(), nullValue() );

        assertThat( client.content()
                          .exists( target.getKey()
                                         .getType(), target.getName(), first ), equalTo( false ) );
        assertThat( client.content()
                          .exists( target.getKey()
                                         .getType(), target.getName(), second ), equalTo( false ) );

        assertThat( client.content()
                          .exists( source.getKey()
                                         .getType(), source.getName(), first ), equalTo( true ) );
        assertThat( client.content()
                          .exists( source.getKey()
                                         .getType(), source.getName(), second ), equalTo( true ) );
    }
}
