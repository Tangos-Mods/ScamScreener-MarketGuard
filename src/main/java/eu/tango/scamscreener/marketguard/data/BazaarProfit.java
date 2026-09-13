package eu.tango.scamscreener.marketguard.data;

import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class BazaarProfit {
    private BazaarProfit() {}

    public static List<Item> collectItems(AbstractContainerMenu menu, int[] slots) {
        if (menu == null || slots == null) {
            return List.of();
        }

        List<Item> items = new ArrayList<>();
        for (int slot : slots) {
            if (slot < 0 || menu.slots.size() <= slot) {
                continue;
            }

            ItemStack stack = menu.getSlot(slot).getItem();
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            String itemId = SkyBlockItemUtil.getSkyblockId(stack);
            String displayName = SkyBlockItemUtil.getDisplayName(stack);
            if (itemId != null || (displayName != null && !displayName.isBlank())) {
                items.add(new Item(itemId, displayName, stack.getCount()));
            }
        }
        return List.copyOf(items);
    }

    public static Summary summarize(Iterable<Item> items, Function<String, BazaarData.LookupResult> lookup) {
        if (items == null || lookup == null) {
            return Summary.empty();
        }

        double total = 0.0;
        int pricedStacks = 0;
        int missingStacks = 0;
        boolean stale = false;
        boolean loading = false;
        boolean refreshFailed = false;

        for (Item item : items) {
            if (item == null || item.count() <= 0) {
                continue;
            }

            String itemId = item.itemId();
            if (itemId == null || itemId.isBlank()) {
                itemId = BazaarData.findItemIdByName(item.displayName());
            }
            if (itemId == null || itemId.isBlank()) {
                missingStacks++;
                continue;
            }

            BazaarData.LookupResult result = lookup.apply(itemId);
            stale |= result.stale();
            loading |= result.loading();
            refreshFailed |= result.refreshFailed();
            if (!result.hasValue()) {
                missingStacks++;
                continue;
            }

            total += item.count() * result.value().sell();
            pricedStacks++;
        }

        return new Summary(total, pricedStacks, missingStacks, stale, loading, refreshFailed);
    }

    public record Item(String itemId, String displayName, int count) {}

    public record Summary(double total, int pricedStacks, int missingStacks, boolean stale, boolean loading, boolean refreshFailed) {
        public static Summary empty() {
            return new Summary(0.0, 0, 0, false, false, false);
        }
    }
}
