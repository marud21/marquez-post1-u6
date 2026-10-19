package com.tienda.pedidos.repository;

import com.tienda.pedidos.dto.ItemPedido;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class PedidoRepository {

    private final JdbcTemplate jdbcTemplate;
    private final InventarioRepository inventario;

    public PedidoRepository(JdbcTemplate jdbcTemplate, InventarioRepository inventario) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventario = inventario;
    }

    /**
     * Guarda el pedido confirmado, su detalle y descuenta el inventario en una sola
     * transacción. El id se obtiene del INSERT (KeyHolder), no de CALL IDENTITY(), que
     * depende de la sesión de base de datos.
     */
    @Transactional
    public Long guardar(Long clienteId, List<ItemPedido> items, double subtotal,
                        double descuento, double impuesto, double total) {
        KeyHolder llave = new GeneratedKeyHolder();
        jdbcTemplate.update(conexion -> {
            PreparedStatement ps = conexion.prepareStatement(
                    "INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)", new String[]{"ID"});
            ps.setLong(1, clienteId);
            ps.setDouble(2, subtotal);
            ps.setDouble(3, descuento);
            ps.setDouble(4, impuesto);
            ps.setDouble(5, total);
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(7, "CONFIRMADO");
            return ps;
        }, llave);
        Long pedidoId = llave.getKey().longValue();

        for (ItemPedido item : items) {
            jdbcTemplate.update(
                    "INSERT INTO detalle_pedido (pedido_id, producto_id, cantidad) VALUES (?, ?, ?)",
                    pedidoId, item.getProductoId(), item.getCantidad());
            inventario.descontar(item.getProductoId(), item.getCantidad());
        }
        return pedidoId;
    }
}
