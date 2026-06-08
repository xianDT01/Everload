package com.EverLoad.everload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YtDiscoverHomeDto {
    private List<YtDiscoverShelfDto> shelves;
    /** Continuation token for {@code /api/ytmusic/discover/continuation}; null when exhausted. */
    private String continuation;
}
