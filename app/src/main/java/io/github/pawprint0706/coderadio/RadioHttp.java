package io.github.pawprint0706.coderadio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class RadioHttp {
    private RadioHttp() {}
    public static StationSnapshot snapshot() throws Exception {
        return NowPlayingParser.parse(new String(get(RadioConfig.API, 1_048_576), StandardCharsets.UTF_8));
    }
    public static byte[] get(String raw, int limit) throws IOException {
        String url = RadioRules.httpsUrl(raw);
        if (url.isEmpty()) throw new IOException("HTTPS URL required");
        for (int redirect = 0; redirect < 5; redirect++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(8_000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", RadioConfig.USER_AGENT);
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Missing redirect");
                    url = RadioRules.httpsUrl(URI.create(url).resolve(location).toString());
                    if (url.isEmpty()) throw new IOException("Unsafe redirect");
                    continue;
                }
                if (status != 200) throw new IOException("HTTP " + status);
                if (connection.getContentLengthLong() > limit) throw new IOException("Response too large");
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
                        if (output.size() + count > limit) throw new IOException("Response too large");
                        output.write(buffer, 0, count);
                    }
                    return output.toByteArray();
                }
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many redirects");
    }
}
