package com.moksha.storyvault.dto;

import lombok.*;
import java.util.List;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthorStats {
    private String name;
    private long count;
    private List<FandomEntry> fandoms;

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FandomEntry {
        private String fandom;
        private long count;
    }
}
