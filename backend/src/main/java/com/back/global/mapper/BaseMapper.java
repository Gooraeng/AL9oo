package com.back.global.mapper;

public interface BaseMapper<D, E> {

    E toEntity(D domain);

    D toDomain(E entity);
}
