package com.iplay.backend.song;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

/**
 * Songs are added by the owner by dropping audio files into the music folder.
 * On startup, the folder is synced with the database: new files get a row
 * (title/artist/album/duration/cover read from the file's tags, falling back to
 * an "Artist - Title.mp3" file name), and rows whose file is gone are removed.
 */
@Component
public class SongLibrary implements ApplicationRunner {

    private final SongRepository repository;
    private final SongMetadataReader metadataReader;
    private final Path musicDir;

    public SongLibrary(SongRepository repository, SongMetadataReader metadataReader,
                       @Value("${app.music-dir}") String musicDir) {
        this.repository = repository;
        this.metadataReader = metadataReader;
        this.musicDir = Path.of(musicDir).toAbsolutePath().normalize();
    }

    public Path getMusicDir() {
        return musicDir;
    }

    @Override
    @CacheEvict(value = "songs", allEntries = true)
    public void run(ApplicationArguments args) throws IOException {
        Files.createDirectories(musicDir);
        try (Stream<Path> files = Files.list(musicDir)) {
            files.filter(Files::isRegularFile).forEach(this::register);
        }
        repository.findAll().stream()
                .filter(song -> !Files.isRegularFile(musicDir.resolve(song.getFileName())))
                .forEach(repository::delete);
    }

    private void register(Path file) {
        String fileName = file.getFileName().toString();
        SongFileName fromName = SongFileName.parse(fileName);
        if (fromName == null) {
            return; // not an audio file we serve
        }
        Song song = repository.findByFileName(fileName).orElseGet(Song::new);
        if (song.getId() != null && song.getDurationSec() != null) {
            return; // already synced
        }

        // Embedded tags win; the file name is only a fallback.
        SongMetadataReader.Metadata meta = metadataReader.read(file);

        song.setFileName(fileName);
        song.setContentType(fromName.contentType());
        song.setTitle(first(meta.title(), fromName.title()));
        song.setArtist(first(meta.artist(), fromName.artist()));
        song.setAlbum(meta.album());
        song.setDurationSec(meta.durationSec() != null ? meta.durationSec() : 0);
        song.setHasCover(meta.hasCover());
        repository.save(song);
    }

    private static String first(String a, String b) {
        return a != null ? a : b;
    }
}
