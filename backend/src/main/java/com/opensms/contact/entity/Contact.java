package com.opensms.contact.entity;

import com.opensms.tenant.entity.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "contacts")
public class Contact {

    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "name", length = 150, nullable = false)
    private String name;

    @Column(name = "phone_number", length = 20, nullable = false)
    private String phoneNumber;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant updatedAt;

    protected Contact() {
        // Required by JPA.
    }

    public Contact(Tenant tenant, String name, String phoneNumber, String email, String description) {
        this.id = UUID.randomUUID();
        this.tenant = Objects.requireNonNull(tenant, "tenant must not be null");
        updateDetails(name, phoneNumber, email, description);
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public String getName() { return name; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getEmail() { return email; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateDetails(String name, String phoneNumber, String email, String description) {
        String normalizedName = Objects.requireNonNull(name, "name must not be null").strip();
        if (normalizedName.isEmpty() || normalizedName.length() > 150) {
            throw new IllegalArgumentException("Contact name must contain 1 to 150 characters.");
        }
        if (phoneNumber == null || !E164_PATTERN.matcher(phoneNumber).matches()) {
            throw new IllegalArgumentException("Phone number must use E.164 format with 8 to 15 digits.");
        }
        String normalizedEmail = normalizeOptional(email);
        String normalizedDescription = normalizeOptional(description);
        if (normalizedEmail != null && normalizedEmail.length() > 254) {
            throw new IllegalArgumentException("Email must not exceed 254 characters.");
        }
        if (normalizedDescription != null && normalizedDescription.length() > 1000) {
            throw new IllegalArgumentException("Description must not exceed 1000 characters.");
        }
        this.name = normalizedName;
        this.phoneNumber = phoneNumber;
        this.email = normalizedEmail;
        this.description = normalizedDescription;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip();
    }

    @PrePersist
    private void initializePersistenceFields() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void updateTimestamp() { updatedAt = Instant.now(); }
}
