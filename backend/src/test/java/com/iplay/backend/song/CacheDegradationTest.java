package com.iplay.backend.song;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Regression test for the failure found in drill 2: with Redis unreachable, a
 * request to the cached endpoint hung indefinitely instead of falling back to the
 * database, because Lettuce queues commands while reconnecting and Spring's
 * default cache error handler rethrows.
 *
 * <p>Redis is pointed at a closed port here, so the cache is genuinely
 * unreachable. The fixes live in {@code config.CacheConfig} and
 * {@code application.properties}; if any of them is removed, this test hangs or
 * fails rather than silently regressing.
 */
@SpringBootTest
@TestPropertySource(properties = {
        // Real Redis cache manager, but aimed at a port nothing is listening on.
        "spring.cache.type=redis",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=59999",
        "spring.data.redis.timeout=200ms",
})
@ActiveProfiles("test")
class CacheDegradationTest {

    @Autowired
    SongService songService;

    @Autowired
    SongRepository repository;

    @Test
    @DisplayName("with Redis unreachable, the song list still comes back from the database")
    void servesFromDatabaseWhenCacheIsUnreachable() {
        Song song = new Song();
        song.setFileName("degradation-test.mp3");
        song.setTitle("Still Served");
        song.setContentType("audio/mpeg");
        song.setDurationSec(1);
        repository.save(song);

        // assertTimeoutPreemptively aborts on a hang instead of blocking the suite,
        // which is exactly the failure mode being guarded against.
        List<Song> songs = assertTimeoutPreemptively(
                Duration.ofSeconds(10), () -> songService.findAll());

        assertThat(songs).extracting(Song::getTitle).contains("Still Served");
    }

    @Test
    @DisplayName("repeated calls stay fast, so no request is left waiting on the dead cache")
    void repeatedCallsDoNotAccumulateDelay() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            songService.findAll();
        }
        long elapsed = System.currentTimeMillis() - start;

        // Before the fix each call blocked until Redis came back. Five calls with
        // a 200ms cache timeout stay well inside this bound.
        assertThat(elapsed).isLessThan(5_000);
    }
}
