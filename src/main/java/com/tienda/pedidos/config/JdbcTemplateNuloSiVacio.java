package com.tienda.pedidos.config;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * El código de partida de GestorPedidos asume que {@code queryForObject} devuelve
 * {@code null} cuando la consulta no encuentra filas ({@code if (tipoCliente == null)}),
 * pero JdbcTemplate lanza {@link EmptyResultDataAccessException}. Este adaptador de
 * infraestructura hace válida esa suposición para poder ejecutar el código de partida
 * sin modificarlo; ver el README, sección "Hallazgos al ejecutar el código de partida".
 */
public class JdbcTemplateNuloSiVacio extends JdbcTemplate {

    public JdbcTemplateNuloSiVacio(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
        try {
            return super.queryForObject(sql, requiredType, args);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
