package eu.tango.scamscreener.marketguard.events;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionInteractEventTest {

    @Test
    void eventInvokesAllRegisteredListeners() {
        AtomicInteger calls = new AtomicInteger();

        AuctionInteractEvent.EVENT.register(context -> calls.incrementAndGet());
        AuctionInteractEvent.EVENT.register(context -> calls.incrementAndGet());

        AuctionInteractEvent.EVENT.invoker().onInteract(null);

        assertEquals(2, calls.get());
    }

    @Test
    void cancelMarksContextAsCancelled() {
        AuctionInteractEvent.Context context = new AuctionInteractEvent.Context(
                null,
                null,
                null,
                null,
                0,
                null,
                null
        );

        assertFalse(context.isCancelled());
        context.cancel();
        assertTrue(context.isCancelled());
    }
}

