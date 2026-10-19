package com.tienda.pedidos.descuento;

import com.tienda.pedidos.validacion.ContextoPedido;
import org.springframework.stereotype.Component;

/** Clientes sin regla propia (ESTANDAR, MOROSO): sin descuento, de forma explícita. */
@Component
public class DescuentoEstandar implements EstrategiaDescuento {

    @Override
    public double calcular(ContextoPedido contexto) {
        return 0.0;
    }
}
