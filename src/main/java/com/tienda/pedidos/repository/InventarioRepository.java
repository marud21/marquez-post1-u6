package com.tienda.pedidos.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class InventarioRepository {

    private final JdbcTemplate jdbcTemplate;

    public InventarioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Stock disponible del producto; vacío si el producto no está en el inventario. */
    public Optional<Integer> stockDe(Long productoId) {
        return jdbcTemplate.queryForList(
                "SELECT stock FROM inventario WHERE producto_id = ?", Integer.class, productoId)
                .stream().findFirst();
    }

    public void descontar(Long productoId, int cantidad) {
        jdbcTemplate.update("UPDATE inventario SET stock = stock - ? WHERE producto_id = ?", cantidad, productoId);
    }
}
