package com.tienda.pedidos.service;

import com.tienda.pedidos.CorreosRegistrados;
import com.tienda.pedidos.SalidaPedidos;
import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.dto.ResultadoPedido;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Campañas CORPORATIVO y VOLUMEN con Black Friday inactiva (valor por defecto).
 * Regla de negocio: se aplica el MAYOR entre el descuento por tipo de cliente y el de campañas.
 * Usa solo la API pública de GestorPedidos: el mismo archivo valida la versión con eslabones
 * de cadena y la versión corregida con Strategy.
 */
@SpringBootTest
@Transactional
@Import(CorreosRegistrados.class)
class CampanasDescuentoTest {

    private static final SalidaPedidos SALIDA = new SalidaPedidos("salida-campanas.txt");

    @Autowired
    private GestorPedidos gestor;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterAll
    static void escribirSalida() throws Exception {
        SALIDA.escribir();
    }

    private ResultadoPedido procesar(String caso, Long clienteId, ItemPedido... items) {
        ResultadoPedido r = gestor.procesarPedido(
                new PedidoRequest(clienteId, "cliente" + clienteId + "@tienda.co", List.of(items)));
        SALIDA.registrar(caso, r, jdbc);
        return r;
    }

    private static double totalEsperado(double subtotal, double descuento) {
        double impuesto = (subtotal - subtotal * descuento) * 0.19;
        return subtotal - (subtotal * descuento) + impuesto;
    }

    private void assertConfirmado(ResultadoPedido r, double subtotal, double descuento) {
        assertTrue(r.isConfirmado(), () -> "Rechazado: " + r.getMotivoRechazo());
        assertEquals(descuento, jdbc.queryForObject(
                "SELECT descuento FROM pedidos WHERE id = ?", Double.class, r.getPedidoId()));
        assertEquals(totalEsperado(subtotal, descuento), r.getTotal());
    }

    @Test
    void clienteConNitRecibe10PorCientoCorporativo() {
        ResultadoPedido r = procesar("C1 CORPORATIVO (NIT)", 6L, new ItemPedido(1L, 2));
        assertConfirmado(r, 200_000, 0.10);
    }

    @Test
    void pedidoDeMasDe20UnidadesRecibe12PorCientoPorVolumen() {
        ResultadoPedido r = procesar("C2 VOLUMEN 25 unidades (ESTANDAR)", 5L, new ItemPedido(4L, 25));
        assertConfirmado(r, 25_000, 0.12);
    }

    @Test
    void exactamente20UnidadesNoAlcanzaElDescuentoPorVolumen() {
        ResultadoPedido r = procesar("C3 VOLUMEN 20 unidades (limite)", 5L, new ItemPedido(4L, 20));
        assertConfirmado(r, 20_000, 0.0);
    }

    @Test
    void vipConVolumenTomaElMayorEntre5Y12PorCiento() {
        ResultadoPedido r = procesar("C4 VIP 5% vs VOLUMEN 12%", 1L, new ItemPedido(4L, 21));
        assertConfirmado(r, 21_000, 0.12);
    }

    @Test
    void vipMayorAUnMillonConservaSu15PorCientoFrenteAlVolumen() {
        ResultadoPedido r = procesar("C5 VIP 15% vs VOLUMEN 12%", 1L, new ItemPedido(2L, 5), new ItemPedido(4L, 21));
        assertConfirmado(r, 1_271_000, 0.15);
    }

    @Test
    void corporativoConVolumenTomaElMayor() {
        ResultadoPedido r = procesar("C6 CORPORATIVO 10% vs VOLUMEN 12%", 6L, new ItemPedido(4L, 30));
        assertConfirmado(r, 30_000, 0.12);
    }

    @Test
    void blackFridayInactivaNoAlteraElDescuento() {
        ResultadoPedido r = procesar("C7 Black Friday inactiva (ESTANDAR)", 5L, new ItemPedido(1L, 1));
        assertConfirmado(r, 100_000, 0.0);
    }

    @Test
    void unaCampanaNoSalvaUnPedidoRechazadoPorValidacion() {
        ResultadoPedido r = procesar("C8 VOLUMEN con stock insuficiente", 6L, new ItemPedido(3L, 25));
        assertFalse(r.isConfirmado());
        assertEquals("Stock insuficiente: producto 3", r.getMotivoRechazo());
    }
}
