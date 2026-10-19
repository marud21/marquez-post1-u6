package com.tienda.pedidos.descuento;

import com.tienda.pedidos.validacion.ContextoPedido;

// Strategy: cada regla encapsula su propio porcentaje de descuento,
// sin depender de un orden de evaluacion frente a las demas
public interface EstrategiaDescuento {

    /** Porcentaje de descuento como fracción (0.15 = 15 %). */
    double calcular(ContextoPedido contexto);
}
