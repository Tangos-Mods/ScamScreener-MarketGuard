package eu.tango.scamscreener.marketguard.profittracker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProfileResolverTest {
    @AfterEach
    void clearProfileId() {
        ProfileResolver.clearCurrentProfileId();
    }

    @Test
    void usesProfileIdFromHypixelSystemMessage() {
        ProfileResolver.handleSystemMessage("Profile ID: 74FF3103-1A88-46D5-8841-1E2A6610D0EB");

        assertEquals(
                "74ff3103-1a88-46d5-8841-1e2a6610d0eb",
                ProfileResolver.resolveCurrentProfileId(null)
        );
    }

    @Test
    void clearsCachedIdWhenHypixelReportsProfileChange() {
        ProfileResolver.handleSystemMessage("Profile ID: 74ff3103-1a88-46d5-8841-1e2a6610d0eb");
        ProfileResolver.handleSystemMessage("Your profile was changed to: Raspberry (Co-op)");

        assertNull(ProfileResolver.resolveCurrentProfileId(null));
    }
}
