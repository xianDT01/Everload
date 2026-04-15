package com.EverLoad.everload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MaintenanceStatusDto {
    private boolean active;
    private String message;
}