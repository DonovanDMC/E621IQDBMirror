package cool.furry.e621;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

public class IQDBProxy {
    private final Logger LOGGER = Logger.getLogger(PostExportDownloader.class.getName());
    private final String baseURL;
    private final OkHttpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    public IQDBProxy(String baseURL) {
        this.baseURL = baseURL;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .writeTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .build();
    }

    public record Channels(RGBInfo channels) {}
    public record RGBInfo(List<Integer> r, List<Integer> g, List<Integer> b) {}

    public void add(String id, RGBInfo info) {
        String url = String.format("%s/images/%s", baseURL, id);
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "IQDBMirror/1.0.0 (donovan_dmc)")
                    .header("Content-Type", "application/json")
                    .method("POST", RequestBody.create(objectMapper.writeValueAsString(new Channels(info)), MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    LOGGER.info(String.format("Successfully added post #%s to iqdb", id));
                } else {
                    LOGGER.warning(String.format("Failed to add post #%s to iqdb", id));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
