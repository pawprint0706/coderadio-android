import io.github.pawprint0706.coderadio.*;
import java.util.Objects;

/** Runs against production classes with only a JDK: no SDK, network, mocks or test framework. */
public final class CoreChecks {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) {
        equal("Flitz&Suppe", RadioRules.recoverSong("", "Funkaholic", "Flitz&Suppe - Funkaholic")[0]);
        equal("fly", RadioRules.recoverSong("", "fly", " - fly")[1]);
        equal("Untagged track", RadioRules.recoverSong("Unknown", "Unknown", " — Untagged track")[1]);
        equal("Code Radio", RadioRules.recoverSong(null, null, null)[1]);
        equal("A", RadioRules.recoverSong("", "", "A — B - C")[0]);
        equal("B - C", RadioRules.recoverSong("", "", "A — B - C")[1]);
        equal("Existing", RadioRules.recoverSong("Existing", "Title", "Other - Track")[0]);
        equal("", RadioRules.httpsUrl("http://example.com/stream"));
        equal("", RadioRules.httpsUrl("file:///local"));
        equal("", RadioRules.httpsUrl("https://user:pass@example.com/stream"));
        equal("", RadioRules.httpsUrl("https://example.com/stream#fragment"));
        equal("https://example.com/live", RadioRules.httpsUrl(" https://example.com/live "));
        equal("https://coderadio-admin-v2.freecodecamp.org/art.jpg", RadioRules.artworkUrl("/art.jpg"));
        equal("", RadioRules.artworkUrl("data:image/png;base64,test"));
        equal(0.0f, RadioRules.volume(-10));
        equal(1.0f, RadioRules.volume(999));
        equal(true, Math.abs(RadioRules.volume(50) - 0.59460356f) < 0.00001);
        equal(1_000L, RadioRules.retryDelay(0));
        equal(2_000L, RadioRules.retryDelay(1));
        equal(30_000L, RadioRules.retryDelay(50));
        PlaybackIntent intent = new PlaybackIntent();
        equal(false, intent.requested());
        intent.play();
        long failedStreamToken = intent.generation();
        equal(true, intent.mayRetry(failedStreamToken));
        intent.pause();
        equal(false, intent.mayRetry(failedStreamToken));
        intent.play();
        equal(false, intent.mayRetry(failedStreamToken));
        equal(true, intent.mayRetry(intent.generation()));
        intent.pause();
        equal(false, intent.mayRetry(intent.generation()));
        StationSnapshot s = new StationSnapshot(true, "T", "A", "", "", "https://host/high", "", "https://host/default", -1);
        equal("https://host/high", s.stream(64));
        equal(0, s.listeners);
        StationSnapshot low = new StationSnapshot(true, "T", "A", "", "", "https://host/high", "https://host/low", "", 1);
        equal("https://host/low", low.stream(64));
        equal("https://host/high", low.stream(128));
        System.out.println("PASS: " + checks + " production core regression checks");
    }
}
