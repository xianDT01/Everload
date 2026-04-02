package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.NasPath;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NasPathRepository extends JpaRepository<NasPath, Long> {
    boolean existsByName(String name);
}