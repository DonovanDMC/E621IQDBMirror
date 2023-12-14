package cool.furry.e621;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

@SuppressWarnings("FieldCanBeLocal")
public class DownloadTask implements Callable<String> {

    private final int RESIZE_WIDTH = 128;
    private final int RESIZE_HEIGHT = 128;
    private final IQDBProxy iqdb;
    private final IPostInfo post;
    public DownloadTask(IQDBProxy iqdb, IPostInfo post) {
        this.iqdb = iqdb;
        this.post = post;
    }

    @Override
    public String call() {
        Logger.getLogger(this.getClass().getName()).info(String.format("Downloading post #%s (%s)", post.id(), post.url()));
        iqdb.add(post.id(), download(post));
        return post.id();
    }

    @SuppressWarnings("DuplicatedCode")
    public IQDBProxy.RGBInfo download(IPostInfo post) {
        try{
            int[] data = ((DataBufferInt) resize(ImageIO.read(new URL(post.url()).openStream())).getRaster().getDataBuffer()).getData();
            List<Integer> r = new ArrayList<>();
            List<Integer> b = new ArrayList<>();
            List<Integer> g = new ArrayList<>();

            for (int i = 0; i < data.length; i++) {
                Color c = new Color(data[i]);
                r.add(i, c.getRed());
                g.add(i, c.getGreen());
                b.add(i, c.getBlue());
            }

            return new IQDBProxy.RGBInfo(r, g, b);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private BufferedImage resize(BufferedImage image) {
        BufferedImage resizedImage = new BufferedImage(RESIZE_WIDTH, RESIZE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = resizedImage.createGraphics();
        graphics2D.drawImage(image, 0, 0, RESIZE_WIDTH, RESIZE_HEIGHT, null);
        graphics2D.dispose();
        return resizedImage;
    }
}
