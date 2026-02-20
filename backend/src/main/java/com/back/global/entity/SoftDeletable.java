package com.back.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class SoftDeletable extends Editable {

    @Column(name = "is_deleted")
    private boolean deleted;

    public void markAsDeleted() {
        this.deleted = true;
    }
}
