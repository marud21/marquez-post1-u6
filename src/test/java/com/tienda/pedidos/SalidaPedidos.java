package com.tienda.pedidos;

import com.tienda.pedidos.dto.ResultadoPedido;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Registra la salida observable de cada pedido de prueba (resultado, descuento guardado,
 * total) y la escribe en target/ para comparar la versión original con la refactorizada.
 */
public class SalidaPedidos {

    private final String archivo;
    private final List<String> lineas = new ArrayList<>();

    public SalidaPedidos(String archivo) {
        this.archivo = archivo;
    }

    public void registrar(String caso, ResultadoPedido r, JdbcTemplate jdbc) {
        String linea;
        if (r.isConfirmado()) {
            Double descuento = jdbc.queryForObject(
                    "SELECT descuento FROM pedidos WHERE id = ?", Double.class, r.getPedidoId());
            linea = String.format("%-40s | CONFIRMADO | descuento=%-4s | total=%s",
                    caso, Math.round(descuento * 100) + "%", r.getTotal());
        } else {
            linea = String.format("%-40s | RECHAZADO  | %s", caso, r.getMotivoRechazo());
        }
        lineas.add(linea);
    }

    public void escribir() throws IOException {
        List<String> ordenadas = new ArrayList<>(lineas);
        ordenadas.sort(String::compareTo);
        Path destino = Path.of("target", archivo);
        Files.createDirectories(destino.getParent());
        Files.write(destino, ordenadas, StandardCharsets.UTF_8);
        System.out.println("\n===== " + archivo + " =====");
        ordenadas.forEach(System.out::println);
    }
}
