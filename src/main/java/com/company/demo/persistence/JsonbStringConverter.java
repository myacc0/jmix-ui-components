package com.company.demo.persistence;

import org.eclipse.persistence.mappings.DatabaseMapping;
import org.eclipse.persistence.mappings.converters.Converter;
import org.eclipse.persistence.sessions.Session;
import org.postgresql.util.PGobject;

import java.sql.SQLException;

/**
 * Converts a {@code String} attribute to a PostgreSQL {@code jsonb} column and back.
 * EclipseLink otherwise binds String attributes as {@code varchar}, which Postgres
 * rejects for jsonb columns ("column is of type jsonb but expression is of type character varying").
 */
public class JsonbStringConverter implements Converter {

    private static final String JSONB_TYPE = "jsonb";

    @Override
    public Object convertObjectValueToDataValue(Object objectValue, Session session) {
        if (objectValue == null) {
            return null;
        }
        PGobject pgObject = new PGobject();
        pgObject.setType(JSONB_TYPE);
        try {
            pgObject.setValue((String) objectValue);
        } catch (SQLException e) {
            throw new IllegalArgumentException("Invalid JSON value: " + objectValue, e);
        }
        return pgObject;
    }

    @Override
    public Object convertDataValueToObjectValue(Object dataValue, Session session) {
        if (dataValue instanceof PGobject pgObject) {
            return pgObject.getValue();
        }
        return dataValue == null ? null : dataValue.toString();
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public void initialize(DatabaseMapping mapping, Session session) {
    }
}
