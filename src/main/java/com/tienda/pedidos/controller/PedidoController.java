package com.tienda.pedidos.controller;

import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.dto.ResultadoPedido;
import com.tienda.pedidos.service.GestorPedidos;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Punto de entrada HTTP para probar el sistema con mvn spring-boot:run. */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final GestorPedidos gestorPedidos;

    public PedidoController(GestorPedidos gestorPedidos) {
        this.gestorPedidos = gestorPedidos;
    }

    @PostMapping
    public ResponseEntity<ResultadoPedido> procesar(@RequestBody PedidoRequest request) {
        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);
        return resultado.isConfirmado()
                ? ResponseEntity.status(201).body(resultado)
                : ResponseEntity.unprocessableEntity().body(resultado);
    }
}
