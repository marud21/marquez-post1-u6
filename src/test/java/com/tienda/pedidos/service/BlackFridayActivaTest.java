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

/** Campaña BLACK_FRIDAY activa (25 % fijo): gana frente a cualquier otro descuento. */
@SpringBootTest(properties = "promo.black-friday.activa=true")
@Transactional
@Import(CorreosRegistrados.class)
class BlackFridayActivaTest {

    private static final SalidaPedidos SALIDA = new SalidaPedidos("salida-black-friday.txt");

    @Autowired
    private GestorPedidos gestor;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CorreosRegistrados.Registro correos;

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
    void clienteEstandarRecibe25PorCiento() {
        ResultadoPedido r = procesar("B1 Black Friday (ESTANDAR)", 5L, new ItemPedido(1L, 2));
        assertConfirmado(r, 200_000, 0.25);
        assertTrue(correos.ultimo().cuerpo().contains("Descuento aplicado: 25%"));
    }

    @Test
    void blackFridaySuperaAlVipDe15PorCiento() {
        ResultadoPedido r = procesar("B2 Black Friday vs VIP 15%", 1L, new ItemPedido(2L, 5));
        assertConfirmado(r, 1_250_000, 0.25);
    }

    @Test
    void blackFridaySuperaACorporativoYVolumenJuntos() {
        ResultadoPedido r = procesar("B3 Black Friday vs CORPORATIVO + VOLUMEN", 6L, new ItemPedido(4L, 30));
        assertConfirmado(r, 30_000, 0.25);
    }

    @Test
    void blackFridayNoSalvaUnPedidoDeClienteInexistente() {
        ResultadoPedido r = procesar("B4 Black Friday con cliente inexistente", 99L, new ItemPedido(1L, 1));
        assertFalse(r.isConfirmado());
        assertEquals("Cliente no registrado", r.getMotivoRechazo());
    }
}
