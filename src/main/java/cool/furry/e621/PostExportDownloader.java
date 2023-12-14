package cool.furry.e621;

import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.UnescapedQuoteHandling;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class PostExportDownloader {
    private final Logger LOGGER = Logger.getLogger(PostExportDownloader.class.getName());
    private final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private final Date DATE;
    private final IQDBProxy iqdb;
    private final @Nullable String apiUser;
    private final @Nullable String apiKey;
    private final Set<String> EXTENSIONS = Set.of("png", "jpg", "gif", "webm", "mp4");
    private final Path TEMP;
    private final Path STATE = Paths.get(String.format("%s/e621-posts-export-download-state", System.getProperty("java.io.tmpdir")));

    public PostExportDownloader(IQDBProxy iqdb, Date date, @Nullable String apiUser, @Nullable String apiKey) {
        this.iqdb = iqdb;
        this.DATE = date;
        this.apiUser = apiUser;
        this.apiKey = apiKey;
        try {
            this.TEMP = Files.createTempDirectory("e621-posts-export");
            TEMP.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary directory", e);
        }
    }

    public PostExportDownloader(IQDBProxy iqdb, @Nullable String apiUser, @Nullable String apiKey) {
        this(iqdb, new Date(), apiUser, apiKey);
    }

    @SuppressWarnings("unused")
    public PostExportDownloader(IQDBProxy iqdb, Date date) {
        this(iqdb, date, null, null);
    }

    @SuppressWarnings("unused")
    public PostExportDownloader(IQDBProxy iqdb) {
        this(iqdb, null, null);
    }

    public ParsedInfo run() {
        try {
            String name = String.format("posts-%s", FORMAT.format(DATE));
            String url = String.format("https://e621.net/db_export/%s.csv.gz", name);
            File compressedOutFile = TEMP.resolve(String.format("%s.csv.gz", name)).toFile();
            compressedOutFile.deleteOnExit();
            File outFile = TEMP.resolve(String.format("%s.csv", name)).toFile();
            outFile.deleteOnExit();
            try (FileOutputStream compressedOutputStream = new FileOutputStream(compressedOutFile); FileOutputStream outputStream = new FileOutputStream(outFile)) {
                ReadableByteChannel channel = Channels.newChannel(new URL(url).openStream());
                compressedOutputStream.getChannel().transferFrom(channel, 0, Long.MAX_VALUE);
                LOGGER.info("Export Downloaded");
                try (GZIPInputStream gzInput = new GZIPInputStream(new FileInputStream(compressedOutFile))) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = gzInput.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, len);
                    }
                    LOGGER.info("Export Extracted");

                    return parse(outFile);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public @Nullable Set<String> getState() {
        @Nullable Set<String> state = null;
        if(Files.exists(STATE)) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(STATE.toFile()));
                String text = reader.readLine();
                if(text != null && !text.isEmpty()) {
                    state = new HashSet<>(Arrays.asList(text.split(",")));
                }
                reader.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return state;
    }

    public void appendState(Set<String> additions) {
        @Nullable Set<String> state = getState();
        if (state == null) {
            state = new HashSet<>();
        }

        Set<String> combined = Stream.concat(state.stream(), additions.stream()).collect(Collectors.toSet());

        saveState(combined);
    }

    public void saveState(Set<String> state) {
        try {
            LOGGER.info("Saved state");
            BufferedWriter writer = new BufferedWriter(new FileWriter(STATE.toFile()));
            writer.write(String.join(",", state));
            writer.close();
        } catch (IOException e) {
            LOGGER.severe("Failed to save state");
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    // id (0), uploader_id (1), created_at (2), md5 (3), source (4), rating (5), image_width (6), image_height (7), tag_string (8),
    // locked_tags (9), fav_count (10), file_ext (11), parent_id (12), change_seq (13), approver_id (14), file_size (15),
    // comment_count (16), description (17), duration (18), updated_at (19), is_deleted (20), is_pending (21), is_flagged (22),
    // score (23), up_score (24), down_score (25), is_rating_locked (26), is_status_locked (27), is_note_locked (28)
    public ParsedInfo parse(File file) {
        int total = 0, skipped = 0;
        @Nullable Set<String> completed = getState();
        CsvParserSettings settings = new CsvParserSettings();
        settings.setUnescapedQuoteHandling(UnescapedQuoteHandling.STOP_AT_CLOSING_QUOTE);
        settings.setMaxCharsPerColumn(200_000);
        settings.setHeaderExtractionEnabled(true);
        CsvParser parser = new CsvParser(settings);
        Set<String> deletedPosts = new HashSet<>();
        Set<PostInfo> posts = new HashSet<>();
        for (Record record : parser.iterateRecords(file)) {
            total++;
            String id = record.getString(0);
            if (completed != null && completed.contains(id)) {
                continue;
            }

            String md5 = record.getString(3);
            String ext = record.getString(11);
            boolean isDeleted = record.getString(20).equals("t");
            if(!EXTENSIONS.contains(ext)) {
                continue;
            }
            if(isDeleted) {
                deletedPosts.add(id);
                continue;
            }

            posts.add(new PostInfo(id, md5));
        }

        parser.stopParsing();

        LOGGER.info(String.format("Got %s active posts, %s deleted posts (skipped %s)", posts.size(), deletedPosts.size(), skipped));

        return new ParsedInfo(deletedPosts, posts);
    }

    public record PostInfo(String id, String md5) implements IPostInfo {
        public String url() {
            return String.format("https://static1.e621.net/data/preview/%s/%s/%s.jpg", md5.substring(0, 2), md5.substring(2, 4), md5);
        }
    }
    public record ParsedInfo(Set<String> deletedPosts, Set<PostInfo> posts) {}

    public void downloadAll(ParsedInfo info) {
        int processed = 0, active = 0, deleted = 0;
        int total = info.posts.size();
        Set<String> completed = new HashSet<>();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CompletionService<String> service = new ExecutorCompletionService<>(executor);

        info.posts.forEach(post -> service.submit(new DownloadTask(iqdb, post)));

        if (apiUser != null && apiKey != null) {
            total += info.deletedPosts.size();
            LOGGER.info("APIKey & User present, attempting to download deleted posts");
            DeletedPostsDownloader downloader = new DeletedPostsDownloader(info.deletedPosts, apiUser, apiKey);
            Set<DeletedPostsDownloader.DeletedPostInfo> deletedPostInfos = downloader.getPosts();
            deletedPostInfos.forEach(post -> service.submit(new DownloadTask(iqdb, post)));
        }

        while(processed < total) {
            try {
                String id = service.take().get();
                completed.add(id);
                if(info.deletedPosts.contains(id)) {
                    deleted++;
                } else {
                    active++;
                }
                if ((++processed % 100) == 0) {
                    appendState(completed);
                    completed.clear();
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        appendState(completed);
        completed.clear();
        executor.shutdown();

        LOGGER.info(String.format("Finished downloading %s posts (%s active, %s deleted)", total, active, deleted));
    }
}
