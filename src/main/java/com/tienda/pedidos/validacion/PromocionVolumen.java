package com.tienda.pedidos.validacion;

// Nuevo eslabon: volumen -- necesita el subtotal de items, que el contexto todavia
// no expone en unidades, asi que recalcula la cantidad total el mismo desde el request
@org.springframework.stereotype.Component
public class PromocionVolumen extends ValidadorPedido {
    @Override
    protected void ejecutar(ContextoPedido contexto) {
        int totalUnidades = contexto.getRequest().getItems().stream()
            .mapToInt(item -> item.getCantidad()).sum();
        if (totalUnidades > 20) {
            contexto.aplicarDescuentoCampana(0.12);
        }
    }
}
