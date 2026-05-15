package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.ArtistProfile;
import com.EverLoad.everload.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArtistProfileRepository extends JpaRepository<ArtistProfile, Long> {
    List<ArtistProfile> findAllByOrderByNameAsc();
    List<ArtistProfile> findByUserOrderByNameAsc(User user);
    Optional<ArtistProfile> findByIdAndUser(Long id, User user);
    Optional<ArtistProfile> findFirstByNameIgnoreCaseOrderByIdAsc(String name);
}
