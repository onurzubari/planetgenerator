package com.onur.planetgen.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Path;

public final class ImageUtil {
    private ImageUtil() {}

    public static void saveARGB(int[][] argb, Path path) throws IOException {
        int h = argb.length, w = argb[0].length;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, argb[y][x]);
            }
        }
        ImageIO.write(img, "PNG", path.toFile());
    }

    public static void saveGray8(int[][] gray, Path path) throws IOException {
        int h = gray.length, w = gray[0].length;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster r = img.getRaster();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                r.setSample(x, y, 0, gray[y][x] & 0xFF);
            }
        }
        ImageIO.write(img, "PNG", path.toFile());
    }

    public static void saveGray16(float[][] f, Path path) throws IOException {
        int h = f.length, w = f[0].length;
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float v = f[y][x];
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        double inv = 1.0 / Math.max(1e-9, (max - min));

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_USHORT_GRAY);
        WritableRaster r = img.getRaster();
        short[] row = new short[w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v16 = (int) Math.round((f[y][x] - min) * inv * 65535.0);
                row[x] = (short) (v16 & 0xFFFF);
            }
            r.setDataElements(0, y, w, 1, row);
        }
        ImageIO.write(img, "PNG", path.toFile());
    }
}
