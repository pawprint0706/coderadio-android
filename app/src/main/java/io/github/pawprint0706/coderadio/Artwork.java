package io.github.pawprint0706.coderadio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class Artwork {
    private Artwork() {}
    public static byte[] fallback(Context context) {
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.app_icon);
        return compress(bitmap);
    }
    public static byte[] download(String url) throws IOException {
        byte[] data = RadioHttp.get(url, 5 * 1024 * 1024);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Invalid artwork");
        int sample = 1;
        while (Math.max(options.outWidth, options.outHeight) / sample > 512) sample *= 2;
        options.inSampleSize = sample;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (bitmap == null) throw new IOException("Artwork decode failed");
        return compress(bitmap);
    }
    private static byte[] compress(Bitmap bitmap) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output);
        bitmap.recycle();
        return output.toByteArray();
    }
}
