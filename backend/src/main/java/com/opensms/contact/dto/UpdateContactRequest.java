package com.opensms.contact.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateContactRequest(
        @NotBlank(message = "Contact name must not be blank.")
        @Size(max = 150, message = "Contact name must not exceed 150 characters.")
        String name,
        @NotBlank(message = "Phone number must not be blank.")
        @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$",
                message = "Phone number must use E.164 format with 8 to 15 digits.")
        String phoneNumber,
        @Email(message = "Email address must be valid.")
        @Size(max = 254, message = "Email must not exceed 254 characters.")
        String email,
        @Size(max = 1000, message = "Description must not exceed 1000 characters.")
        String description
) {
}
