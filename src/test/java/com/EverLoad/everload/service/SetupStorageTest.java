package com.everload.everload.service;

import com.everload.everload.repository.*;
import java.nio.file.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SetupStorageTest {
    @TempDir Path root;
    NasService nas;
    NasPathRepository paths;
    @BeforeEach void prepare() {
        paths = mock(NasPathRepository.class);
        nas = new NasService(paths, mock(FavoriteTrackRepository.class), mock(PlaybackHistoryRepository.class),
                mock(TrackMetadataCacheRepository.class), mock(PlaylistTrackRepository.class), mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(nas, "nasStorageBase", root.toString());
    }
    @Test void browsesOnlySubfoldersAndReturnsActualContainerRoot() throws Exception {
        Files.createDirectory(root.resolve("Music")); Files.writeString(root.resolve("song.mp3"), "example");
        var result = nas.inspectStorage(""); assertTrue(result.readable());
        assertEquals(root.toString(), result.root()); assertEquals(1, result.folders().size());
        assertEquals("Music", result.folders().get(0).relativePath());
    }
    @Test void missingMountIsNotConfusedWithReadOnly() {
        ReflectionTestUtils.setField(nas, "nasStorageBase", root.resolve("missing").toString());
        var result = nas.inspectStorage(""); assertFalse(result.readable()); assertFalse(result.writable());
        assertThrows(IllegalArgumentException.class, () -> nas.createSetupLibrary("Music", ""));
        verify(paths, never()).save(any());
    }
    @Test void rejectsTraversalAndAbsoluteHostPaths() {
        assertThrows(SecurityException.class, () -> nas.inspectStorage("../outside"));
        assertThrows(SecurityException.class, () -> nas.inspectStorage(root.toAbsolutePath().toString()));
    }
    @Test void readOnlyFileStoreRemainsReadableWithoutOfferingWriteAccess() throws Exception {
        FileStore store = mock(FileStore.class); when(store.isReadOnly()).thenReturn(true);
        Path target = root.toRealPath();
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.getFileStore(target)).thenReturn(store);
            var result = nas.inspectStorage(""); assertTrue(result.readable()); assertFalse(result.writable());
        }
    }
    @Test void rejectsSymlinkEscapingTheMountedRootWhenPlatformAllowsLinks() throws Exception {
        Path outside = Files.createTempDirectory("everload-outside-");
        try {
            try { Files.createSymbolicLink(root.resolve("escape"), outside); }
            catch (java.io.IOException | UnsupportedOperationException e) { Assumptions.abort("Symbolic links unavailable on this platform"); }
            assertThrows(SecurityException.class, () -> nas.inspectStorage("escape"));
        } finally { Files.deleteIfExists(outside); }
    }
}
