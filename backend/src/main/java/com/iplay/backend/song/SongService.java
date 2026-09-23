package com.iplay.backend.song;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import java.util.List;

@Service
public class SongService {

    private final SongRepository repository;

    public SongService(SongRepository repository) {
        this.repository = repository;
    }

    @Cacheable("songs")
    public List<Song> findAll() {
        return repository.findAll();
    }
    
}
