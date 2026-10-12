package com.everload.everload.service;

import com.everload.everload.model.TrackMetadataCache;
import com.everload.everload.repository.TrackMetadataCacheRepository;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import static org.junit.jupiter.api.Assertions.*;

class LibraryTracksRepositoryTest {
    private static SessionFactory factory;

    @BeforeAll static void setup() {
        factory = new Configuration().addAnnotatedClass(TrackMetadataCache.class)
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:library-pages;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .buildSessionFactory();
    }
    @AfterAll static void close() { if (factory != null) factory.close(); }

    @Test void paginatesDeterministicallyAndIsolatesNasPaths() {
        try (var session = factory.openSession()) {
            session.beginTransaction();
            session.persist(track(1L, "folder/b.mp3", "Same", "Artist"));
            session.persist(track(1L, "folder/a.mp3", "Same", "Artist"));
            session.persist(track(2L, "private.mp3", "Private", "Other"));
            session.flush();
            var repo = new JpaRepositoryFactory(session).getRepository(TrackMetadataCacheRepository.class);
            var first = repo.findLibraryPage(1L, "", PageRequest.of(0, 1));
            var second = repo.findLibraryPage(1L, "", PageRequest.of(1, 1));
            assertTrue(first.hasNext());
            assertFalse(second.hasNext());
            assertEquals("folder/a.mp3", first.getContent().get(0).getRelativePath());
            assertEquals("folder/b.mp3", second.getContent().get(0).getRelativePath());
            session.getTransaction().rollback();
        }
    }
    @Test void searchesLiteralTextAcrossMetadataAndPathsWithoutWildcardExpansion() {
        try (var session = factory.openSession()) {
            session.beginTransaction();
            session.persist(track(1L, "folder/song.mp3", "100% song", "Tacabro"));
            session.persist(track(1L, "other.mp3", "Unrelated", null));
            session.flush();
            var repo = new JpaRepositoryFactory(session).getRepository(TrackMetadataCacheRepository.class);
            assertEquals(1, repo.findLibraryPage(1L, "tacabro", PageRequest.of(0, 100)).getNumberOfElements());
            assertEquals(1, repo.findLibraryPage(1L, "%", PageRequest.of(0, 100)).getNumberOfElements());
            assertEquals(1, repo.findLibraryPage(1L, "folder/", PageRequest.of(0, 100)).getNumberOfElements());
            assertTrue(repo.findLibraryPage(1L, "no match", PageRequest.of(0, 100)).isEmpty());
            session.getTransaction().rollback();
        }
    }
    private TrackMetadataCache track(Long pathId, String path, String title, String artist) {
        return TrackMetadataCache.builder().nasPathId(pathId).relativePath(path).title(title).artist(artist).build();
    }

    @Test void sortsAcrossPagesByArtistOrNewestModification() {
        try (var session = factory.openSession()) {
            session.beginTransaction();
            var old = track(1L, "old.mp3", "Alpha", "Zulu");
            old.setLastModified(100);
            var recent = track(1L, "recent.mp3", "Zulu", "Alpha");
            recent.setLastModified(200);
            session.persist(old);
            session.persist(recent);
            session.persist(track(2L, "private.mp3", "Private", "AAA"));
            session.flush();
            var repo = new JpaRepositoryFactory(session).getRepository(TrackMetadataCacheRepository.class);
            for (String sort : java.util.List.of("artist", "date")) {
                var first = repo.findSortedLibraryPage(1L, "", sort, PageRequest.of(0, 1));
                assertEquals("recent.mp3", first.getContent().get(0).getRelativePath());
                assertTrue(first.hasNext());
                var second = repo.findSortedLibraryPage(1L, "", sort, PageRequest.of(1, 1));
                assertEquals("old.mp3", second.getContent().get(0).getRelativePath());
                assertFalse(second.hasNext());
            }
            assertEquals(1, repo.findSortedLibraryPage(1L, "recent", "date", PageRequest.of(0, 10)).getNumberOfElements());
            session.getTransaction().rollback();
        }
    }
}
