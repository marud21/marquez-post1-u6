package com.tienda.pedidos.descuento;

import com.tienda.pedidos.repository.ClienteRepository;
import com.tienda.pedidos.validacion.ContextoPedido;
import org.springframework.stereotype.Component;

/** CORPORATIVO: 10 % para clientes con NIT registrado. */
@Component
public class DescuentoCorporativo implements CampanaDescuento {

    private final ClienteRepository clientes;

    public DescuentoCorporativo(ClienteRepository clientes) {
        this.clientes = clientes;
    }

    @Override
    public double calcular(ContextoPedido contexto) {
        return clientes.nitDe(contexto.getRequest().getClienteId()).isPresent() ? 0.10 : 0.0;
    }
}
