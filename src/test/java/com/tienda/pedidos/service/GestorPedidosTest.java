package com.tienda.pedidos.service;

import com.tienda.pedidos.CorreosRegistrados;
import com.tienda.pedidos.SalidaPedidos;
import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.dto.ResultadoPedido;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba de caracterización: fija el comportamiento observable de procesarPedido()
 * (rechazo o confirmación, motivo, descuento guardado, total, inventario y correo).
 * Usa solo la API pública de GestorPedidos, por lo que el mismo archivo se ejecuta
 * sin cambios antes y después de refactorizar.
 *
 * Cada prueba corre en una transacción que se revierte al final, así los datos
 * semilla (stock, historial de pedidos) son los mismos para todos los casos.
 */
@SpringBootTest
@Transactional
@Import(CorreosRegistrados.class)
class GestorPedidosTest {

    private static final LocalTime ANTES_DEL_CORTE = LocalTime.of(10, 0);
    private static final LocalTime DESPUES_DEL_CORTE = LocalTime.of(21, 30);
    private static final SalidaPedidos SALIDA = new SalidaPedidos("salida-pedidos.txt");

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

    /** Procesa el pedido con la hora del día fijada (la regla de mora depende de LocalTime.now()). */
    private ResultadoPedido procesar(String caso, LocalTime hora, Long clienteId, ItemPedido... items) {
        try (MockedStatic<LocalTime> reloj = Mockito.mockStatic(LocalTime.class, Mockito.CALLS_REAL_METHODS)) {
            reloj.when(LocalTime::now).thenReturn(hora);
            ResultadoPedido r = gestor.procesarPedido(
                    new PedidoRequest(clienteId, "cliente" + clienteId + "@tienda.co", List.of(items)));
            SALIDA.registrar(caso, r, jdbc);
            return r;
        }
    }

    private ResultadoPedido procesar(String caso, Long clienteId, ItemPedido... items) {
        return procesar(caso, ANTES_DEL_CORTE, clienteId, items);
    }

    /** Misma fórmula del código de partida: impuesto del 19 % sobre el subtotal con descuento. */
    private static double totalEsperado(double subtotal, double descuento) {
        double impuesto = (subtotal - subtotal * descuento) * 0.19;
        return subtotal - (subtotal * descuento) + impuesto;
    }

    private Double descuentoGuardado(ResultadoPedido r) {
        return jdbc.queryForObject("SELECT descuento FROM pedidos WHERE id = ?", Double.class, r.getPedidoId());
    }

    private int stock(long productoId) {
        return jdbc.queryForObject("SELECT stock FROM inventario WHERE producto_id = ?", Integer.class, productoId);
    }

    private void assertConfirmado(ResultadoPedido r, double subtotal, double descuento) {
        assertTrue(r.isConfirmado(), () -> "Se esperaba confirmado pero fue rechazado: " + r.getMotivoRechazo());
        assertNotNull(r.getPedidoId());
        assertEquals(descuento, descuentoGuardado(r));
        assertEquals(totalEsperado(subtotal, descuento), r.getTotal());
    }

    // ---------- Rutas de validación ----------

    @Test
    void pedidoSinItemsSeRechaza() {
        ResultadoPedido r = procesar("01 sin items", 1L);
        assertFalse(r.isConfirmado());
        assertEquals("El pedido no contiene items", r.getMotivoRechazo());
    }

    @Test
    void stockInsuficienteSeRechazaSinTocarElInventario() {
        int correosAntes = correos.cantidad();
        ResultadoPedido r = procesar("02 stock insuficiente", 1L, new ItemPedido(1L, 1), new ItemPedido(3L, 5));
        assertFalse(r.isConfirmado());
        assertEquals("Stock insuficiente: producto 3", r.getMotivoRechazo());
        assertEquals(2, stock(3));
        assertEquals(correosAntes, correos.cantidad(), "Un pedido rechazado no envía correo");
    }

    @Test
    void clienteInexistenteSeRechaza() {
        ResultadoPedido r = procesar("03 cliente inexistente", 99L, new ItemPedido(1L, 1));
        assertFalse(r.isConfirmado());
        assertEquals("Cliente no registrado", r.getMotivoRechazo());
    }

    @Test
    void clienteMorosoDentroDelHorarioDeCorteSeRechaza() {
        ResultadoPedido r = procesar("04 moroso 10:00 (dentro del corte)", ANTES_DEL_CORTE, 4L, new ItemPedido(1L, 2));
        assertFalse(r.isConfirmado());
        assertEquals("Cliente con deuda pendiente: $150000.0", r.getMotivoRechazo());
    }

    @Test
    void clienteMorosoFueraDelHorarioDeCorteSeConfirmaSinDescuento() {
        ResultadoPedido r = procesar("05 moroso 21:30 (fuera del corte)", DESPUES_DEL_CORTE, 4L, new ItemPedido(1L, 2));
        assertConfirmado(r, 200_000, 0.0);
    }

    // ---------- Descuentos por tipo de cliente ----------

    @Test
    void vipConSubtotalMayorAUnMillonTiene15PorCiento() {
        ResultadoPedido r = procesar("06 VIP > 1.000.000", 1L, new ItemPedido(2L, 5));
        assertConfirmado(r, 1_250_000, 0.15);
    }

    @Test
    void vipConSubtotalEntre500MilYUnMillonTiene10PorCiento() {
        ResultadoPedido r = procesar("07 VIP 500.000 - 1.000.000", 1L, new ItemPedido(1L, 6));
        assertConfirmado(r, 600_000, 0.10);
    }

    @Test
    void vipConSubtotalMenorTiene5PorCiento() {
        ResultadoPedido r = procesar("08 VIP <= 500.000", 1L, new ItemPedido(1L, 2));
        assertConfirmado(r, 200_000, 0.05);
    }

    @Test
    void frecuenteConMasDe10PedidosTiene8PorCiento() {
        ResultadoPedido r = procesar("09 FRECUENTE > 10 pedidos", 2L, new ItemPedido(1L, 3));
        assertConfirmado(r, 300_000, 0.08);
    }

    @Test
    void frecuenteConEntre4Y10PedidosTiene4PorCiento() {
        ResultadoPedido r = procesar("10 FRECUENTE 4 - 10 pedidos", 3L, new ItemPedido(1L, 1), new ItemPedido(4L, 10));
        assertConfirmado(r, 110_000, 0.04);
    }

    @Test
    void clienteEstandarNoTieneDescuento() {
        ResultadoPedido r = procesar("11 ESTANDAR", 5L, new ItemPedido(2L, 1));
        assertConfirmado(r, 250_000, 0.0);
    }

    // ---------- Efectos de un pedido confirmado ----------

    @Test
    void unPedidoConfirmadoDescuentaInventarioGuardaDetalleYEnviaCorreo() {
        ResultadoPedido r = procesar("12 efectos (VIP, 2 productos)", 1L, new ItemPedido(1L, 4), new ItemPedido(2L, 1));
        assertConfirmado(r, 650_000, 0.10);

        assertEquals(96, stock(1));
        assertEquals(49, stock(2));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM detalle_pedido WHERE pedido_id = ?", Integer.class, r.getPedidoId()));

        CorreosRegistrados.Correo correo = correos.ultimo();
        assertEquals("cliente1@tienda.co", correo.destinatario());
        assertEquals("Confirmacion de pedido #" + r.getPedidoId(), correo.asunto());
        double total = totalEsperado(650_000, 0.10);
        double impuesto = (650_000 - 650_000 * 0.10) * 0.19;
        assertEquals("Estimado cliente,\n\nSu pedido ha sido confirmado.\n"
                + "Subtotal: $650000.0\n"
                + "Descuento aplicado: 10%\n"
                + "Impuesto: $" + impuesto + "\nTotal: $" + total + "\n", correo.cuerpo());
    }

    @Test
    void unPedidoSinDescuentoNoMencionaDescuentoEnElCorreo() {
        procesar("13 correo sin descuento (ESTANDAR)", 5L, new ItemPedido(1L, 1));
        assertFalse(correos.ultimo().cuerpo().contains("Descuento"));
    }
}
