package com.opensms.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateMessageRequest(
        @NotBlank(message = "Destination phone number must not be blank.")
        @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$",
                message = "Destination phone number must use E.164 format with 8 to 15 digits.")
        String destinationPhoneNumber,

        @NotBlank(message = "Message body must not be blank.")
        String body
) {
}
