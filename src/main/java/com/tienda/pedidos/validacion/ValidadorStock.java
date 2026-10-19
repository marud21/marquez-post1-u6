package com.tienda.pedidos.validacion;

import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.repository.InventarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Eslabon 1: el pedido tiene items y hay stock para cada uno
@Component
public class ValidadorStock extends ValidadorPedido {

    private static final Logger log = LoggerFactory.getLogger(ValidadorStock.class);

    private final InventarioRepository inventario;

    public ValidadorStock(InventarioRepository inventario) {
        this.inventario = inventario;
    }

    @Override
    protected void ejecutar(ContextoPedido contexto) {
        var items = contexto.getRequest().getItems();
        if (items == null || items.isEmpty()) {
            log.warn("Pedido rechazado: sin items. Cliente {}", contexto.getRequest().getClienteId());
            contexto.rechazar("El pedido no contiene items");
            return;
        }
        for (ItemPedido item : items) {
            int stock = inventario.stockDe(item.getProductoId()).orElse(0);
            if (stock < item.getCantidad()) {
                log.warn("Stock insuficiente para producto {}", item.getProductoId());
                contexto.rechazar("Stock insuficiente: producto " + item.getProductoId());
                return;
            }
        }
    }
}
