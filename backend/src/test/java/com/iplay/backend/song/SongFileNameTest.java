package com.iplay.backend.song;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure logic, no Spring: how a file name is turned into artist/title/content type
 * when a file carries no embedded tags.
 */
class SongFileNameTest {

    @Test
    @DisplayName("Artist - Title.mp3 splits into artist and title")
    void splitsArtistAndTitle() {
        SongFileName parsed = SongFileName.parse("Daft Punk - Around the World.mp3");

        assertThat(parsed.artist()).isEqualTo("Daft Punk");
        assertThat(parsed.title()).isEqualTo("Around the World");
        assertThat(parsed.contentType()).isEqualTo("audio/mpeg");
    }

    @Test
    @DisplayName("a name with no separator is all title, with no artist")
    void noSeparatorMeansTitleOnly() {
        SongFileName parsed = SongFileName.parse("Around the World.mp3");

        assertThat(parsed.artist()).isNull();
        assertThat(parsed.title()).isEqualTo("Around the World");
    }

    @Test
    @DisplayName("only the FIRST separator splits, so extra ones stay in the title")
    void splitsOnFirstSeparatorOnly() {
        SongFileName parsed = SongFileName.parse("Daft Punk - Around the World - Remix.mp3");

        assertThat(parsed.artist()).isEqualTo("Daft Punk");
        assertThat(parsed.title()).isEqualTo("Around the World - Remix");
    }

    @Test
    @DisplayName("a real library file: uploader suffixes end up inside the title")
    void documentsRealWorldYoutubeStyleName() {
        // Named "Title - Artist - Uploader", which is not the Artist - Title
        // convention, so the fields come out misleading. Tagging the file fixes
        // it, because embedded tags take priority over the file name.
        SongFileName parsed = SongFileName.parse(
                "Mere Naseeb Mein (Remix) - Baby H Prem & Hardeep Megha Chatterji "
                        + "- SonyMusicIndiaVEVO (128k).mp3");

        assertThat(parsed.artist()).isEqualTo("Mere Naseeb Mein (Remix)");
        assertThat(parsed.title())
                .isEqualTo("Baby H Prem & Hardeep Megha Chatterji - SonyMusicIndiaVEVO (128k)");
    }

    @Test
    @DisplayName("surrounding whitespace is trimmed")
    void trimsWhitespace() {
        SongFileName parsed = SongFileName.parse("  Daft Punk   -   Around the World  .mp3");

        assertThat(parsed.artist()).isEqualTo("Daft Punk");
        assertThat(parsed.title()).isEqualTo("Around the World");
    }

    @Test
    @DisplayName("extensions are matched case-insensitively")
    void extensionIsCaseInsensitive() {
        assertThat(SongFileName.parse("song.MP3").contentType()).isEqualTo("audio/mpeg");
        assertThat(SongFileName.parse("song.FlAc").contentType()).isEqualTo("audio/flac");
    }

    @Test
    @DisplayName("every supported audio type maps to its media type")
    void mapsEverySupportedType() {
        assertThat(SongFileName.parse("a.mp3").contentType()).isEqualTo("audio/mpeg");
        assertThat(SongFileName.parse("a.wav").contentType()).isEqualTo("audio/wav");
        assertThat(SongFileName.parse("a.ogg").contentType()).isEqualTo("audio/ogg");
        assertThat(SongFileName.parse("a.m4a").contentType()).isEqualTo("audio/mp4");
        assertThat(SongFileName.parse("a.flac").contentType()).isEqualTo("audio/flac");
    }

    @Test
    @DisplayName("non-audio files and files with no extension are rejected")
    void rejectsNonAudio() {
        assertThat(SongFileName.parse("cover.jpg")).isNull();
        assertThat(SongFileName.parse("notes.txt")).isNull();
        assertThat(SongFileName.parse("README")).isNull();
    }

    @Test
    @DisplayName("a leading separator is not treated as an empty artist")
    void leadingSeparatorIsNotAnEmptyArtist() {
        SongFileName parsed = SongFileName.parse(" - Around the World.mp3");

        assertThat(parsed.artist()).isNull();
        assertThat(parsed.title()).isEqualTo("- Around the World");
    }
}
