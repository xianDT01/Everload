package com.EverLoad.everload.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UpdateCheckDto {
    // ── Common ─────────────────────────────────────────────────────────────────
    private boolean updateAvailable;
    private boolean checkConfigured;
    private String error;

    // ── Commit-based tracking (commits API) ────────────────────────────────────
    private String currentCommit;   // short SHA deployed
    private String latestCommit;    // short SHA on main
    private String commitMessage;   // latest commit message
    private String commitDate;      // ISO date of latest commit
    private String commitUrl;       // link to the commit on GitHub

    // ── Release-based tracking (releases API — kept for compatibility) ─────────
    private String currentVersion;
    private String latestVersion;
    private String releaseUrl;
    private String releaseNotes;
}