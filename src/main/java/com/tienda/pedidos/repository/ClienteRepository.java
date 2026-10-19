package com.tienda.pedidos.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ClienteRepository {

    private final JdbcTemplate jdbcTemplate;

    public ClienteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Tipo del cliente (VIP, FRECUENTE, MOROSO, ...); vacío si el cliente no existe. */
    public Optional<String> tipoDe(Long clienteId) {
        return jdbcTemplate.queryForList(
                "SELECT tipo_cliente FROM clientes WHERE id = ?", String.class, clienteId)
                .stream().findFirst();
    }

    /** Suma de las facturas sin pagar; 0 si no tiene ninguna. */
    public double deudaPendiente(Long clienteId) {
        Double deuda = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(monto), 0) FROM facturas WHERE cliente_id = ? AND pagada = false",
                Double.class, clienteId);
        return deuda == null ? 0 : deuda;
    }

    public int pedidosPrevios(Long clienteId) {
        Integer cantidad = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pedidos WHERE cliente_id = ?", Integer.class, clienteId);
        return cantidad == null ? 0 : cantidad;
    }
}
