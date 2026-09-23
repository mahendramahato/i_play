package com.iplay.backend.song;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/songs")
public class SongController {

    private final SongRepository repository;
    private final SongMetadataReader metadataReader;
    private final SongService songService;
    private final Path musicDir;

    public SongController(SongRepository repository, SongLibrary library, 
        SongMetadataReader metadataReader, SongService songService) {
        this.repository = repository;
        this.metadataReader = metadataReader;
        this.songService = songService;
        this.musicDir = library.getMusicDir();
    }

    @GetMapping
    public List<Song> list() {
        return songService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Song> get(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Spring serves ResponseEntity<Resource> with HTTP Range support, so seeking works.
    @GetMapping("/{id}/stream")
    public ResponseEntity<Resource> stream(@PathVariable Long id) throws IOException {
        Song song = repository.findById(id).orElse(null);
        if (song == null) {
            return ResponseEntity.notFound().build();
        }
        Path file = musicDir.resolve(song.getFileName()).normalize();
        if (!file.startsWith(musicDir) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(song.getContentType()))
                .body(new PathResource(file));
    }

    @GetMapping("/{id}/cover")
    public ResponseEntity<byte[]> cover(@PathVariable Long id) {
        Song song = repository.findById(id).orElse(null);
        if (song == null || !Boolean.TRUE.equals(song.getHasCover())) {
            return ResponseEntity.notFound().build();
        }
        Path file = musicDir.resolve(song.getFileName()).normalize();
        if (!file.startsWith(musicDir)) {
            return ResponseEntity.notFound().build();
        }
        SongMetadataReader.Cover cover = metadataReader.readCover(file);
        if (cover == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(cover.mimeType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS))
                .body(cover.data());
    }
}
