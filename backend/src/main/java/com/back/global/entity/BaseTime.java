package com.back.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass
public abstract class BaseTime extends BaseEntity {

    @CreatedDate
    @Column
    private LocalDateTime createdDate;

}
