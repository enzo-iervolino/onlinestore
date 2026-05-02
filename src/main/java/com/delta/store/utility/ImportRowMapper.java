package com.delta.store.utility;

import tools.jackson.databind.JsonNode;

/**
 * Interfaccia generica per trasformare una riga CSV/TXT/JSON
 * in un oggetto del dominio o in una DTO.
 *
 * @param <T> tipo dell'oggetto da importare, esempio ProductDTO.
 */
public interface ImportRowMapper<T> {

    /**
     * Converte una riga CSV/TXT in una DTO.
     */
    T fromColumns(String[] columns, int rowNumber);

    /**
     * Converte un oggetto JSON in una DTO.
     */
    T fromJsonNode(JsonNode node, int rowNumber);
}