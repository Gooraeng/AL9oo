package com.back.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass
public abstract class Editable extends BaseTime {

    @LastModifiedDate
    @Column
    private LocalDateTime modifiedDate;
}
