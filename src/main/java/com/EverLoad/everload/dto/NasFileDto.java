package com.EverLoad.everload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NasFileDto {
    private String name;
    private String path;
    private boolean directory;
    private long size;
    private String lastModified;
}