package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import eu.tango.scamscreener.marketguard.util.MessageBuilder;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuctionPricingResolverTest {

    @Test
    void missingAuctionItemDoesNotCancelWhenFailureShouldNotBlock() {
        AuctionInteractEvent.Context context = mock(AuctionInteractEvent.Context.class);
        LocalPlayer player = null;

        try (MockedStatic<MessageBuilder> messageBuilder = mockStatic(MessageBuilder.class)) {
            assertNull(abortPricing(context, player, false));

            verify(context, never()).cancel();
            messageBuilder.verify(() -> MessageBuilder.error(any(), isNull()));
        }
    }

    @Test
    void missingAuctionItemCancelsWhenFailureShouldBlock() {
        AuctionInteractEvent.Context context = mock(AuctionInteractEvent.Context.class);
        LocalPlayer player = null;

        try (MockedStatic<MessageBuilder> messageBuilder = mockStatic(MessageBuilder.class)) {
            assertNull(abortPricing(context, player, true));

            verify(context).cancel();
            messageBuilder.verify(() -> MessageBuilder.error(any(), isNull()));
        }
    }

    private static AuctionPricingResolver.PricingData abortPricing(
            AuctionInteractEvent.Context context,
            LocalPlayer player,
            boolean cancelOnFailure
    ) {
        try {
            Method method = AuctionPricingResolver.class.getDeclaredMethod(
                    "abortPricing",
                    AuctionInteractEvent.Context.class,
                    LocalPlayer.class,
                    Component.class,
                    boolean.class
            );
            method.setAccessible(true);
            return (AuctionPricingResolver.PricingData) method.invoke(
                    null,
                    context,
                    player,
                    Component.literal("Could not find Auction Item"),
                    cancelOnFailure
            );
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
