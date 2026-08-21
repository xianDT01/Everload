package com.everload.everload.controller;

import com.everload.everload.dto.NasFileDto;
import com.everload.everload.dto.NasPathDto;
import com.everload.everload.service.NasService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NasControllerTest {

    private NasService nasService;
    private NasController controller;

    @BeforeEach
    void setUp() {
        nasService = mock(NasService.class);
        controller = new NasController(nasService);
    }

    @Test
    void successfulOperationsDelegateToNasService() throws Exception {
        NasPathDto path = mock(NasPathDto.class);
        NasFileDto file = mock(NasFileDto.class);
        MockMultipartFile image = new MockMultipartFile("image", "cover.png", "image/png", new byte[]{1});
        MockMultipartFile music = new MockMultipartFile("files", "song.mp3", "audio/mpeg", new byte[]{2});
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(nasService.getAllPaths()).thenReturn(List.of(path));
        when(nasService.createPath(path)).thenReturn(path);
        when(nasService.listFiles(1L, "music")).thenReturn(List.of(file));
        when(nasService.renameFileOrFolder(1L, "old", "new")).thenReturn("folder/new");
        when(nasService.uploadMusicFiles(1L, "music", List.of(music), List.of("album/song.mp3")))
                .thenReturn(List.of(Map.of("uploaded", true)));

        assertEquals(List.of(path), controller.getPaths().getBody());
        assertEquals(path, controller.createPath(path).getBody());
        assertEquals(HttpStatus.OK, controller.deletePath(1L).getStatusCode());
        assertEquals(List.of(file), controller.browse(1L, "music").getBody());
        assertEquals(HttpStatus.OK, controller.mkdir(1L, "music", "new").getStatusCode());
        assertEquals(HttpStatus.OK, controller.delete(1L, "song.mp3").getStatusCode());
        assertEquals(HttpStatus.OK, controller.rename(1L, "old", "new").getStatusCode());
        assertEquals(HttpStatus.OK, controller.move(1L, "song.mp3", "archive").getStatusCode());
        assertEquals(HttpStatus.OK, controller.uploadFolderCover(1L, "music", image).getStatusCode());
        assertEquals(HttpStatus.OK,
                controller.uploadFiles(1L, "music", List.of(music), List.of("album/song.mp3")).getStatusCode());
        controller.downloadFile(1L, "song.mp3", response);
        controller.downloadFolderZip(1L, "album", response);
        assertEquals(HttpStatus.OK, controller.copyFile(1L, "song.mp3", 2L, "backup").getStatusCode());

        verify(nasService).createFolder(1L, "music", "new");
        verify(nasService).deleteFileOrFolder(1L, "song.mp3");
        verify(nasService).moveFileOrFolder(1L, "song.mp3", "archive");
        verify(nasService).saveFolderCover(1L, "music", new byte[]{1}, "image/png");
        verify(nasService).downloadFileToResponse(1L, "song.mp3", response);
        verify(nasService).downloadFolderZipToResponse(1L, "album", response);
        verify(nasService).copyFileTo(1L, "song.mp3", 2L, "backup");
    }

    @Test
    void securityFailuresAreMappedWithoutLeakingExceptions() throws Exception {
        SecurityException denied = new SecurityException("denied");
        MockMultipartFile image = new MockMultipartFile("image", "cover.png", "image/png", new byte[]{1});
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(nasService.createPath(any())).thenThrow(denied);
        when(nasService.listFiles(1L, null)).thenThrow(denied);
        doThrow(denied).when(nasService).createFolder(1L, null, "new");
        doThrow(denied).when(nasService).deleteFileOrFolder(1L, "file");
        when(nasService.renameFileOrFolder(1L, "old", "new")).thenThrow(denied);
        doThrow(denied).when(nasService).moveFileOrFolder(1L, "file", null);
        doThrow(denied).when(nasService).saveFolderCover(eq(1L), eq(null), any(), eq("image/png"));
        when(nasService.uploadMusicFiles(eq(1L), eq(null), anyList(), eq(null))).thenThrow(denied);
        doThrow(denied).when(nasService).downloadFileToResponse(1L, "file", response);
        doThrow(denied).when(nasService).downloadFolderZipToResponse(1L, "folder", response);
        doThrow(denied).when(nasService).copyFileTo(1L, "file", 2L, "dest");

        assertEquals(HttpStatus.BAD_REQUEST, controller.createPath(mock(NasPathDto.class)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.browse(1L, null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.mkdir(1L, null, "new").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.delete(1L, "file").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.rename(1L, "old", "new").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.move(1L, "file", null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.uploadFolderCover(1L, null, image).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.uploadFiles(1L, null, List.of(image), null).getStatusCode());
        controller.downloadFile(1L, "file", response);
        controller.downloadFolderZip(1L, "folder", response);
        assertEquals(HttpStatus.BAD_REQUEST, controller.copyFile(1L, "file", 2L, "dest").getStatusCode());
        verify(response, times(2)).sendError(403, "denied");
    }

    @Test
    void validationAndIoFailuresUseBadRequestOrServerError() throws Exception {
        IllegalArgumentException invalid = new IllegalArgumentException("invalid");
        HttpServletResponse response = mock(HttpServletResponse.class);
        NasPathDto path = mock(NasPathDto.class);
        when(nasService.createPath(path)).thenThrow(invalid);
        doThrow(invalid).when(nasService).deletePath(9L);
        when(nasService.listFiles(9L, "bad")).thenThrow(invalid);
        doThrow(new IOException("disk")).when(nasService).copyFileTo(1L, "file", 2L, "dest");
        doThrow(invalid).when(nasService).downloadFileToResponse(9L, "bad", response);
        doThrow(invalid).when(nasService).downloadFolderZipToResponse(9L, "bad", response);

        assertEquals(HttpStatus.BAD_REQUEST, controller.createPath(path).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.deletePath(9L).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.browse(9L, "bad").getStatusCode());
        controller.downloadFile(9L, "bad", response);
        controller.downloadFolderZip(9L, "bad", response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.copyFile(1L, "file", 2L, "dest").getStatusCode());
        verify(response, times(2)).sendError(400, "invalid");
    }
}
