package com.iplay.backend.song;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Syncing the music folder into the database, against a real (in-memory) database
 * so the queries and the unique constraint on file_name are genuinely exercised.
 * Only the tag reader is mocked, since real audio files would be needed otherwise.
 */
@DataJpaTest
@ActiveProfiles("test")
class SongLibraryTest {

    /**
     * @EnableCaching on the application class demands a CacheManager that the JPA
     * slice does not provide. SongLibrary is constructed directly here, so its
     * @CacheEvict never runs anyway — this test is about folder/database sync.
     */
    @TestConfiguration
    static class NoCaching {
        @Bean
        CacheManager cacheManager() {
            return new NoOpCacheManager();
        }
    }

    @TempDir
    Path musicDir;

    @Autowired
    SongRepository repository;

    @MockitoBean
    SongMetadataReader metadataReader;

    private SongLibrary library;

    @BeforeEach
    void setUp() {
        // No embedded tags, so every field falls back to the file name.
        given(metadataReader.read(any())).willReturn(
                new SongMetadataReader.Metadata(null, null, null, 210, false));
        library = new SongLibrary(repository, metadataReader, musicDir.toString());
    }

    private void addFile(String name) throws Exception {
        Files.writeString(musicDir.resolve(name), "audio");
    }

    private void scan() throws Exception {
        library.run(null);
    }

    @Test
    @DisplayName("a new audio file gets a row, with fields taken from its name")
    void newFileBecomesASong() throws Exception {
        addFile("Daft Punk - Around the World.mp3");

        scan();

        assertThat(repository.findAll()).singleElement().satisfies(song -> {
            assertThat(song.getArtist()).isEqualTo("Daft Punk");
            assertThat(song.getTitle()).isEqualTo("Around the World");
            assertThat(song.getContentType()).isEqualTo("audio/mpeg");
            assertThat(song.getDurationSec()).isEqualTo(210);
        });
    }

    @Test
    @DisplayName("non-audio files in the folder are ignored")
    void ignoresNonAudioFiles() throws Exception {
        addFile("cover.jpg");
        addFile("notes.txt");
        addFile("Real Song.mp3");

        scan();

        assertThat(repository.findAll())
                .extracting(Song::getFileName)
                .containsExactly("Real Song.mp3");
    }

    @Test
    @DisplayName("scanning twice does not duplicate rows")
    void rescanDoesNotDuplicate() throws Exception {
        addFile("Song.mp3");

        scan();
        scan();

        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("a row whose file has been deleted is removed on the next scan")
    void removesRowsForDeletedFiles() throws Exception {
        addFile("Temporary.mp3");
        scan();
        assertThat(repository.findAll()).hasSize(1);

        Files.delete(musicDir.resolve("Temporary.mp3"));
        scan();

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("embedded tags win over the file name")
    void tagsTakePriorityOverFileName() throws Exception {
        given(metadataReader.read(any())).willReturn(
                new SongMetadataReader.Metadata("Real Title", "Real Artist", "Real Album", 180, true));
        addFile("Whatever - Badly Named.mp3");

        scan();

        assertThat(repository.findAll()).singleElement().satisfies(song -> {
            assertThat(song.getTitle()).isEqualTo("Real Title");
            assertThat(song.getArtist()).isEqualTo("Real Artist");
            assertThat(song.getAlbum()).isEqualTo("Real Album");
            assertThat(song.getHasCover()).isTrue();
        });
    }

    @Test
    @DisplayName("a missing music folder is created rather than failing")
    void createsMissingMusicFolder() throws Exception {
        Path absent = musicDir.resolve("not-created-yet");
        SongLibrary fresh = new SongLibrary(repository, metadataReader, absent.toString());

        fresh.run(null);

        assertThat(Files.isDirectory(absent)).isTrue();
    }
}
