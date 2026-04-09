package eu.tango.scamscreener.marketguard.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModrinthUpdateCheckerTest {

    @Test
    void ignoresSameVersionEvenWithLeadingV() {
        String responseBody = """
                [
                  {
                    "version_number": "v1.1.0",
                    "version_type": "release",
                    "date_published": "2026-04-01T10:15:30Z",
                    "status": "listed",
                    "changelog": "Nothing new"
                  }
                ]
                """;

        ModrinthUpdateChecker.UpdateInfo updateInfo = ModrinthUpdateChecker.parseLatestUpdate(responseBody, "1.1.0");

        assertNull(updateInfo);
    }

    @Test
    void picksNewestListedRelease() {
        String responseBody = """
                [
                  {
                    "version_number": "1.2.0-beta1",
                    "version_type": "beta",
                    "date_published": "2026-04-03T10:15:30Z",
                    "status": "listed",
                    "changelog": "Beta build"
                  },
                  {
                    "version_number": "1.1.1",
                    "version_type": "release",
                    "date_published": "2026-04-02T10:15:30Z",
                    "status": "listed",
                    "changelog": "Fixes"
                  },
                  {
                    "version_number": "1.1.2",
                    "version_type": "release",
                    "date_published": "2026-04-04T10:15:30Z",
                    "status": "draft",
                    "changelog": "Draft"
                  }
                ]
                """;

        ModrinthUpdateChecker.UpdateInfo updateInfo = ModrinthUpdateChecker.parseLatestUpdate(responseBody, "1.1.0");

        assertNotNull(updateInfo);
        assertEquals("1.1.0", updateInfo.currentVersion());
        assertEquals("1.1.1", updateInfo.latestVersion());
        assertEquals("https://modrinth.com/project/ji4JdpCu", updateInfo.modrinthUrl());
        assertEquals("Fixes", updateInfo.changelog());
    }
}
