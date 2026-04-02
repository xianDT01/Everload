package com.EverLoad.everload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NasPathDto {
    private Long id;
    private String name;
    private String path;
    private String description;
    private boolean readable;
    private boolean writable;
}