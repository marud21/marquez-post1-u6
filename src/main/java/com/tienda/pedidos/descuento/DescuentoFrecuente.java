package com.tienda.pedidos.descuento;

import com.tienda.pedidos.repository.ClienteRepository;
import com.tienda.pedidos.validacion.ContextoPedido;
import org.springframework.stereotype.Component;

@Component
public class DescuentoFrecuente implements EstrategiaDescuento {

    private final ClienteRepository clientes;

    public DescuentoFrecuente(ClienteRepository clientes) {
        this.clientes = clientes;
    }

    @Override
    public double calcular(ContextoPedido contexto) {
        int pedidosPrevios = clientes.pedidosPrevios(contexto.getRequest().getClienteId());
        if (pedidosPrevios > 10) return 0.08;
        if (pedidosPrevios > 3) return 0.04;
        return 0.0;
    }
}
