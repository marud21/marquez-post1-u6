package com.tienda.pedidos.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProductoRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public double precioDe(Long productoId) {
        return jdbcTemplate.queryForList("SELECT precio FROM productos WHERE id = ?", Double.class, productoId)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Producto sin precio registrado: " + productoId));
    }
}
