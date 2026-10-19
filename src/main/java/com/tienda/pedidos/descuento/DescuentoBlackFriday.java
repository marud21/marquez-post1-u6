package com.tienda.pedidos.descuento;

import com.tienda.pedidos.validacion.ContextoPedido;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** BLACK_FRIDAY: 25 % fijo mientras la campaña esté activa (promo.black-friday.activa). */
@Component
public class DescuentoBlackFriday implements CampanaDescuento {

    private final boolean campanaActiva;

    public DescuentoBlackFriday(@Value("${promo.black-friday.activa}") boolean campanaActiva) {
        this.campanaActiva = campanaActiva;
    }

    @Override
    public double calcular(ContextoPedido contexto) {
        return campanaActiva ? 0.25 : 0.0;
    }
}
