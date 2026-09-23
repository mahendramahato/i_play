package com.iplay.backend.song;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SongRepository extends JpaRepository<Song, Long> {

    Optional<Song> findByFileName(String fileName);
}
