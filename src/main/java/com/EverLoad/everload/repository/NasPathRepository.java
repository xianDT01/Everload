package com.everload.everload.repository;

import com.everload.everload.model.NasPath;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NasPathRepository extends JpaRepository<NasPath, Long> {
    boolean existsByName(String name);
}