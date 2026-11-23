package com.opensms.contact.mapper;

import com.opensms.contact.dto.ContactResponse;
import com.opensms.contact.entity.Contact;
import org.springframework.stereotype.Component;

@Component
public class ContactMapper {

    public ContactResponse toResponse(Contact contact) {
        if (contact == null) throw new IllegalArgumentException("Contact must not be null.");
        return new ContactResponse(contact.getId(), contact.getName(), contact.getPhoneNumber(),
                contact.getEmail(), contact.getDescription(), contact.getCreatedAt(), contact.getUpdatedAt());
    }
}
