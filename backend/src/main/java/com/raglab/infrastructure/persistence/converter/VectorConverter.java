package com.raglab.infrastructure.persistence.converter;

import com.pgvector.PGvector;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.SQLException;

/**
 * JPA AttributeConverter converting between Java float[] and PGvector / String representation.
 */
@Converter(autoApply = false)
public class VectorConverter implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) {
            return null;
        }
        return new PGvector(attribute).getValue();
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return new PGvector(dbData).toArray();
        } catch (SQLException e) {
            throw new IllegalArgumentException("Error deserializing pgvector string: " + dbData, e);
        }
    }
}
