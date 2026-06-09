package com.moksha.storyvault.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PersonalNoteRequest {

    private String content; // nullable — null clears the note
}
