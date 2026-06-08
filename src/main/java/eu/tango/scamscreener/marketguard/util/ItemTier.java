package eu.tango.scamscreener.marketguard.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;

@Getter @RequiredArgsConstructor
public enum ItemTier {
    
    COMMON("Common", 1, 0xFFFFFF),
    RARE("Rare", 2, 0x5555FF),
    EPIC("Epic", 3, 0xFF55FF),
    LEGENDARY("Legendary", 4, 0xFFAA00),
    MYTHIC("Mythic", 5, 0x55FFFF);


    private final String tierName;
    private final int tier;
    private final int color;

    /**
     * Resolves an {@link ItemTier} by enum constant name (e.g. "EPIC") or display name
     * (e.g. "Epic"), case-insensitive.
     *
     * @param name tier name to resolve
     * @return matching {@link ItemTier}, or {@code null} if no tier matches
     */
    @Nullable
    public static ItemTier fromName(String name) {
        if (name == null || name.isBlank()) return null;
        for (ItemTier itemTier : values()) {
            if (itemTier.name().equalsIgnoreCase(name) || itemTier.tierName.equalsIgnoreCase(name)) {
                return itemTier;
            }
        }
        return null;
    }

    /**
     * Resolves an {@link ItemTier} by numeric tier id.
     *
     * @param id tier id (e.g. 3 for EPIC)
     * @return matching {@link ItemTier}, or {@code null} if no tier matches
     */
    @Nullable
    public static ItemTier fromId(int id) {
        for (ItemTier itemTier : values()) {
            if (itemTier.tier == id) return itemTier;
        }
        return null;
    }

}
