package eu.tango.scamscreener.marketguard.auction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class LowestBIN {

    private static final String URL = "https://moulberry.codes/lowestbin.json";

    public static double getLowestBIN(String itemId) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!json.has(itemId)) throw new Exception("Item not found");
        return json.get(itemId).getAsDouble();
    }


}
