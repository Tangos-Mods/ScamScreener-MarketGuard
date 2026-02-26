package eu.tango.scamscreener.marketguard.util;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class MessageBuilder {

    public static final Text PREFIX = Text.literal("[").formatted(Formatting.GRAY)
            .append("MarketGuard").formatted(Formatting.RED)
            .append("] ").formatted(Formatting.GRAY);

}
