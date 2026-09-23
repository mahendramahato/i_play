package com.iplay.backend.song;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.springframework.stereotype.Component;

/** Reads title/artist/album/duration/cover art from the tags embedded in an audio file. */
@Component
public class SongMetadataReader {

    public record Metadata(String title, String artist, String album, Integer durationSec, boolean hasCover) {
        static Metadata empty() {
            return new Metadata(null, null, null, null, false);
        }
    }

    public record Cover(byte[] data, String mimeType) {
    }

    public SongMetadataReader() {
        // jaudiotagger logs every file it touches at INFO
        Logger.getLogger("org.jaudiotagger").setLevel(Level.WARNING);
    }

    public Metadata read(Path file) {
        try {
            AudioFile audio = AudioFileIO.read(file.toFile());
            Tag tag = audio.getTag();
            Integer duration = audio.getAudioHeader().getTrackLength();
            if (tag == null) {
                return new Metadata(null, null, null, duration, false);
            }
            return new Metadata(
                    blankToNull(tag.getFirst(FieldKey.TITLE)),
                    blankToNull(tag.getFirst(FieldKey.ARTIST)),
                    blankToNull(tag.getFirst(FieldKey.ALBUM)),
                    duration,
                    tag.getFirstArtwork() != null);
        } catch (Exception e) {
            // unreadable or untagged file: caller falls back to the file name
            return Metadata.empty();
        }
    }

    public Cover readCover(Path file) {
        try {
            Tag tag = AudioFileIO.read(file.toFile()).getTag();
            Artwork art = tag == null ? null : tag.getFirstArtwork();
            if (art == null) {
                return null;
            }
            String mime = art.getMimeType() != null ? art.getMimeType() : "image/jpeg";
            return new Cover(art.getBinaryData(), mime);
        } catch (Exception e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
