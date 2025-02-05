package com.codecrush.mymeeting;

public class YUVConverter {
    public static byte[] rgbaToNV21(byte[] rgba, int width, int height) {
        int frameSize = width * height;
        byte[] nv21 = new byte[frameSize * 3 / 2]; // NV21 size: width*height*1.5

        // Convert RGBA to Y (luma) and UV (chroma)
        int yIndex = 0;
        int uvIndex = frameSize;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgbaIndex = (y * width + x) * 4;

                // Extract RGB components (ignore Alpha)
                int r = rgba[rgbaIndex] & 0xFF;
                int g = rgba[rgbaIndex + 1] & 0xFF;
                int b = rgba[rgbaIndex + 2] & 0xFF;

                // Compute YUV using integer arithmetic (optimized for performance)
                int Y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                nv21[yIndex++] = (byte) Math.max(0, Math.min(Y, 255));

                // Compute UV for every 2x2 block
                if ((y % 2 == 0) && (x % 2 == 0)) {
                    // Average RGB values for this 2x2 block
                    int avgR = (r + (x + 1 < width ? (rgba[rgbaIndex + 4] & 0xFF) : r)
                            + (y + 1 < height ? (rgba[rgbaIndex + width * 4] & 0xFF) : r)
                            + (x + 1 < width && y + 1 < height ? (rgba[rgbaIndex + width * 4 + 4] & 0xFF) : r)) / 4;

                    int avgG = (g + (x + 1 < width ? (rgba[rgbaIndex + 5] & 0xFF) : g)
                            + (y + 1 < height ? (rgba[rgbaIndex + width * 4 + 1] & 0xFF) : g)
                            + (x + 1 < width && y + 1 < height ? (rgba[rgbaIndex + width * 4 + 5] & 0xFF) : g)) / 4;

                    int avgB = (b + (x + 1 < width ? (rgba[rgbaIndex + 6] & 0xFF) : b)
                            + (y + 1 < height ? (rgba[rgbaIndex + width * 4 + 2] & 0xFF) : b)
                            + (x + 1 < width && y + 1 < height ? (rgba[rgbaIndex + width * 4 + 6] & 0xFF) : b)) / 4;

                    // Compute UV from averaged RGB
                    int U = ((-38 * avgR - 74 * avgG + 112 * avgB + 128) >> 8) + 128;
                    int V = ((112 * avgR - 94 * avgG - 18 * avgB + 128) >> 8) + 128;

                    // NV21 stores V first, then U
                    nv21[uvIndex++] = (byte) Math.max(0, Math.min(V, 255));
                    nv21[uvIndex++] = (byte) Math.max(0, Math.min(U, 255));
                }
            }
        }
        return nv21;
    }
}
