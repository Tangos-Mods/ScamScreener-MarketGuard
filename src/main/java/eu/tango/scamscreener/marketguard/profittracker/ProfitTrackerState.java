package eu.tango.scamscreener.marketguard.profittracker;

import java.util.LinkedHashMap;
import java.util.Map;

final class ProfitTrackerState {
    Map<String, ProfileProfitState> profiles = new LinkedHashMap<>();

    ProfileProfitState getProfile(String profileId) {
        return profiles.get(profileId);
    }

    ProfileProfitState getOrCreateProfile(String profileId) {
        return profiles.computeIfAbsent(profileId, ignored -> new ProfileProfitState());
    }
}
