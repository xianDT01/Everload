package com.everload.everload.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class InstallationState {
    @Id private Long id = 1L;
    private boolean completed;
    private String codeHash;
}
