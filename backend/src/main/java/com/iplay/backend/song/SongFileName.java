package com.iplay.backend.song;

import java.util.Map;

/**
 * What can be derived from a file name alone, for files whose tags are missing.
 *
 * <p>The convention is "Artist - Title.ext". The <em>first</em> " - " separates
 * the two, so "A - B - C.mp3" reads as artist "A" and title "B - C". A name with
 * no separator is taken to be the title.
 */
record SongFileName(String contentType, String artist, String title) {

    private static final Map<String, String> TYPES = Map.of(
            "mp3", "audio/mpeg",
            "wav", "audio/wav",
            "ogg", "audio/ogg",
            "m4a", "audio/mp4",
            "flac", "audio/flac");

    private static final String SEPARATOR = " - ";

    /** Returns null when the extension is missing or not a supported audio type. */
    static SongFileName parse(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String contentType = dot < 0 ? null : TYPES.get(fileName.substring(dot + 1).toLowerCase());
        if (contentType == null) {
            return null;
        }
        String name = fileName.substring(0, dot);
        int sep = name.indexOf(SEPARATOR);
        String artist = sep > 0 ? name.substring(0, sep).trim() : null;
        String title = sep > 0 ? name.substring(sep + SEPARATOR.length()).trim() : name.trim();
        return new SongFileName(contentType, artist, title);
    }
}
