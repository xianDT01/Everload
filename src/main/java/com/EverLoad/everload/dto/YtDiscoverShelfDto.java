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
public class YtDiscoverShelfDto {
    private String title;
    private String strapline;
    private String moreBrowseId;
    private List<YtDiscoverItemDto> items;
}
