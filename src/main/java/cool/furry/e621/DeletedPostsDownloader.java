package cool.furry.e621;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class DeletedPostsDownloader {
    private final Set<String> posts;
    private final String apiUser;
    private final String apiKey;
    private final OkHttpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    public DeletedPostsDownloader(Set<String> posts, String apiUser, String apiKey) {
        this.posts = posts;
        this.apiUser = apiUser;
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .writeTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .build();
    }

    public Set<DeletedPostInfo> getPosts() {
        Set<DeletedPostInfo> resolvedPosts = new HashSet<>();
        for(List<String> chunk : Iterables.partition(posts, 100)) {
            String url = String.format("https://e621.net/posts.json?tags=id:%s%%20status:any", String.join(",", chunk));
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "IQDBMirror/1.0.0 (donovan_dmc)")
                    .header("Authorization", Credentials.basic(apiUser, apiKey))
                    .method("GET", null)
                    .build();

                try (Response response = client.newCall(request).execute()) {
                    PostsResponse postsResponse = objectMapper.readValue(Objects.requireNonNull(response.body()).string(), PostsResponse.class);
                    for (Post post : postsResponse.posts()) {
                        resolvedPosts.add(new DeletedPostInfo(post.id.toString(), post.file.md5, post.preview.url));
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to fetch deleted posts", e);
                }
        }

        return resolvedPosts;
    }

    private record PostsResponse(Set<Post> posts) {}
    private record Post(Integer id, PostPreview preview, PostFile file) {}
    private record PostPreview(String url) {}
    private record PostFile(String md5) {}
    public record DeletedPostInfo(String id, String md5, String url) implements IPostInfo {}
}
