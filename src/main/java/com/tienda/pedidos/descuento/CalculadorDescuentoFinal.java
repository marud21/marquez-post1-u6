package com.tienda.pedidos.descuento;

import com.tienda.pedidos.validacion.ContextoPedido;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Único lugar donde vive la regla de combinación: el descuento final es el mayor entre
 * la estrategia del tipo de cliente y la mejor campaña activa. Se ejecuta después de la
 * validación y con el subtotal ya calculado, sin escribir en estado compartido.
 */
@Component
public class CalculadorDescuentoFinal {

    private final SelectorEstrategiaDescuento selectorPorCliente;
    private final List<CampanaDescuento> campanas;

    public CalculadorDescuentoFinal(SelectorEstrategiaDescuento selectorPorCliente,
                                    List<CampanaDescuento> campanas) {
        this.selectorPorCliente = selectorPorCliente;
        this.campanas = List.copyOf(campanas);
    }

    public double calcular(ContextoPedido contexto) {
        double porTipoCliente = selectorPorCliente.seleccionar(contexto.getTipoCliente()).calcular(contexto);
        double porCampana = campanas.stream()
                .mapToDouble(campana -> campana.calcular(contexto))
                .max().orElse(0.0);
        return Math.max(porTipoCliente, porCampana);
    }
}
