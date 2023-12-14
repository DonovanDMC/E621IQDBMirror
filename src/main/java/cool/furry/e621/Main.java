package cool.furry.e621;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.*;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws ParseException {

        Options options = new Options()
            .addOption("u", "apiuser", true, "The user for your e621 api key.")
            .addOption("k", "apikey", true, "An e621 api key.")
            .addOption("e", "export", true, "The location of an existing posts export to parse.")
            .addRequiredOption("i", "iqdb", true, "The url of your iqdb instance");

        CommandLineParser parser = new DefaultParser();
        CommandLine cli = parser.parse(options, args);
        @Nullable String apiUser = cli.getOptionValue("apiuser");
        @Nullable String apiKey = cli.getOptionValue("apikey");
        @Nullable String export = cli.getOptionValue("export");
        String iqdbURL = cli.getOptionValue("iqdb");

        IQDBProxy iqdb = new IQDBProxy(iqdbURL);
        PostExportDownloader downloader = new PostExportDownloader(iqdb, apiUser, apiKey);
        PostExportDownloader.ParsedInfo info;
        if (export == null) {
            info = downloader.run();
        } else {
            info = downloader.parse(Paths.get(export).toFile());
        }
        downloader.downloadAll(info);
    }

    @SuppressWarnings({"unused", "ExtractMethodRecommender", "DuplicatedCode"})
    private static void convert() throws IOException {
        String url = "";
        BufferedImage resizedImage = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = resizedImage.createGraphics();
        graphics2D.drawImage(ImageIO.read(new URL(url).openStream()), 0, 0, 128, 128, null);
        graphics2D.dispose();
        int[] data = ((DataBufferInt) resizedImage.getRaster().getDataBuffer()).getData();
        List<Integer> r = new ArrayList<>();
        List<Integer> b = new ArrayList<>();
        List<Integer> g = new ArrayList<>();

        for (int i = 0; i < data.length; i++) {
            Color c = new Color(data[i]);
            r.add(i, c.getRed());
            g.add(i, c.getGreen());
            b.add(i, c.getBlue());
        }

        IQDBProxy.Channels channels = new IQDBProxy.Channels(new IQDBProxy.RGBInfo(r, g, b));
        ObjectMapper objectMapper = new ObjectMapper();
        System.out.println(objectMapper.writeValueAsString(channels));
    }
}