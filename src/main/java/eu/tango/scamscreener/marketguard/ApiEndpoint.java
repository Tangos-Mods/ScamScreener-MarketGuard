package eu.tango.scamscreener.marketguard;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

public final class ApiEndpoint {
    private static final String DEFAULT_BASE_URL = "https://scamscreener.creepans.net";
    private static final String DEV_BASE_URL = "http://localhost:8081";
    private static final String RESOURCE = "/marketguard-api.properties";
    private static final String BASE_URL = loadBaseUrl();

    private ApiEndpoint() {}

    public static String url(String path) {
        return BASE_URL + path;
    }

    public static String baseUrl() {
        return BASE_URL;
    }

    private static String loadBaseUrl() {
        String systemProperty = normalizeBaseUrl(System.getProperty("marketguard.apiBaseUrl"));
        if (systemProperty != null) {
            return systemProperty;
        }

        try (InputStream input = ApiEndpoint.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                return DEFAULT_BASE_URL;
            }

            Properties properties = new Properties();
            properties.load(input);
            String configured = normalizeBaseUrl(properties.getProperty("baseUrl"));
            if (configured != null) {
                return configured;
            }
        } catch (IOException ignored) {
        }
        if (isDevJar()) {
            return DEV_BASE_URL;
        }
        return DEFAULT_BASE_URL;
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            return null;
        }
        return normalized.replaceFirst("/+$", "");
    }

    private static boolean isDevJar() {
        return FabricLoader.getInstance()
                .getModContainer(MarketGuard.MOD_ID)
                .map(container -> container.getOrigin().getPaths())
                .stream()
                .flatMap(java.util.Collection::stream)
                .map(Path::getFileName)
                .filter(java.util.Objects::nonNull)
                .map(Path::toString)
                .anyMatch(name -> name.endsWith("-dev.jar"));
    }
}
