package com.iplay.backend.song;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The HTTP contract the React player depends on: status codes, content types and
 * range support.
 *
 * <p>Built standalone rather than with @WebMvcTest: no Spring context means no
 * database, no CacheManager to satisfy, and no proxying of SongLibrary (which
 * @CacheEvict would otherwise wrap in a proxy the controller cannot accept).
 */
class SongControllerTest {

    private static final byte[] AUDIO = "fake-mp3-bytes-for-testing-purposes".getBytes();

    static Path musicDir;

    MockMvc mvc;
    SongRepository repository;
    SongService songService;
    SongMetadataReader metadataReader;

    @BeforeEach
    void setUp() throws Exception {
        musicDir = Files.createTempDirectory("iplay-controller-test");
        Files.write(musicDir.resolve("song.mp3"), AUDIO);

        repository = Mockito.mock(SongRepository.class);
        songService = Mockito.mock(SongService.class);
        metadataReader = Mockito.mock(SongMetadataReader.class);
        SongLibrary library = Mockito.mock(SongLibrary.class);
        // The controller captures the folder in its constructor, so stub first.
        given(library.getMusicDir()).willReturn(musicDir);

        mvc = MockMvcBuilders
                .standaloneSetup(new SongController(repository, library, metadataReader, songService))
                .build();
    }

    private static Song song(long id, String fileName) {
        Song s = new Song();
        s.setId(id);
        s.setTitle("Test Title");
        s.setArtist("Test Artist");
        s.setFileName(fileName);
        s.setContentType("audio/mpeg");
        return s;
    }

    @Test
    @DisplayName("GET /api/songs returns the list from the (cached) service")
    void listsSongs() throws Exception {
        given(songService.findAll()).willReturn(List.of(song(1, "song.mp3")));

        mvc.perform(get("/api/songs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Test Title"))
                .andExpect(jsonPath("$[0].artist").value("Test Artist"));
    }

    @Test
    @DisplayName("GET /api/songs/{id} returns 404 for an unknown id")
    void unknownSongIs404() throws Exception {
        given(repository.findById(99L)).willReturn(Optional.empty());

        mvc.perform(get("/api/songs/99")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("streaming returns the audio with its content type")
    void streamsAudio() throws Exception {
        given(repository.findById(1L)).willReturn(Optional.of(song(1, "song.mp3")));

        mvc.perform(get("/api/songs/1/stream"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(header().string("Accept-Ranges", "bytes"));
    }

    @Test
    @DisplayName("a Range request returns 206 with only that slice, so seeking works")
    void rangeRequestReturnsPartialContent() throws Exception {
        given(repository.findById(1L)).willReturn(Optional.of(song(1, "song.mp3")));

        mvc.perform(get("/api/songs/1/stream").header("Range", "bytes=0-9"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-9/" + AUDIO.length))
                .andExpect(header().longValue("Content-Length", 10));
    }

    @Test
    @DisplayName("streaming a row whose file has been deleted returns 404, not an error")
    void missingFileIs404() throws Exception {
        given(repository.findById(2L)).willReturn(Optional.of(song(2, "deleted.mp3")));

        mvc.perform(get("/api/songs/2/stream")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a file name escaping the music folder is refused")
    void pathTraversalIsRefused() throws Exception {
        // If the guard in stream() is ever removed, this starts serving /etc/passwd.
        given(repository.findById(3L)).willReturn(Optional.of(song(3, "../../../../etc/passwd")));

        mvc.perform(get("/api/songs/3/stream")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("cover art is returned with its own media type and cached")
    void returnsCoverArt() throws Exception {
        Song withCover = song(4, "song.mp3");
        withCover.setHasCover(true);
        given(repository.findById(4L)).willReturn(Optional.of(withCover));
        given(metadataReader.readCover(any()))
                .willReturn(new SongMetadataReader.Cover(new byte[] {1, 2, 3}, "image/jpeg"));

        mvc.perform(get("/api/songs/4/cover"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(new byte[] {1, 2, 3}))
                .andExpect(header().string("Cache-Control", "max-age=86400"));
    }

    @Test
    @DisplayName("a song with no embedded art returns 404 without touching the file")
    void noCoverIs404() throws Exception {
        Song noCover = song(5, "song.mp3");
        noCover.setHasCover(false);
        given(repository.findById(5L)).willReturn(Optional.of(noCover));

        mvc.perform(get("/api/songs/5/cover")).andExpect(status().isNotFound());
    }
}
