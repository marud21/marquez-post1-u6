package com.tienda.pedidos.descuento;

/**
 * Estrategia de descuento de una campaña promocional. A diferencia de las estrategias por
 * tipo de cliente (de las que se elige exactamente una), todas las campañas se evalúan.
 * CalculadorDescuentoFinal recibe todas las implementaciones registradas como beans.
 */
public interface CampanaDescuento extends EstrategiaDescuento {
}
