package com.alex.messenger.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Embeddable
public class ContactId implements Serializable {

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "contact_user_id", nullable = false)
    private UUID contactUserId;

    public ContactId(UUID ownerUserId, UUID contactUserId) {
        this.ownerUserId = ownerUserId;
        this.contactUserId = contactUserId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ContactId that)) {
            return false;
        }
        return Objects.equals(ownerUserId, that.ownerUserId)
                && Objects.equals(contactUserId, that.contactUserId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerUserId, contactUserId);
    }
}
