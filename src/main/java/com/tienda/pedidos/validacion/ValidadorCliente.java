package com.tienda.pedidos.validacion;

import com.tienda.pedidos.repository.ClienteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.Optional;

// Eslabon 2: el cliente existe y no tiene mora (solo se ejecuta si el stock ya paso)
@Component
public class ValidadorCliente extends ValidadorPedido {

    private static final Logger log = LoggerFactory.getLogger(ValidadorCliente.class);
    private static final LocalTime HORA_DE_CORTE = LocalTime.of(20, 0);

    private final ClienteRepository clientes;

    public ValidadorCliente(ClienteRepository clientes) {
        this.clientes = clientes;
    }

    @Override
    protected void ejecutar(ContextoPedido contexto) {
        Long clienteId = contexto.getRequest().getClienteId();
        Optional<String> tipo = clientes.tipoDe(clienteId);
        if (tipo.isEmpty()) {
            log.warn("Cliente no encontrado: {}", clienteId);
            contexto.rechazar("Cliente no registrado");
            return;
        }
        contexto.setTipoCliente(tipo.get());

        if (tipo.get().equals("MOROSO")) {
            double deuda = clientes.deudaPendiente(clienteId);
            boolean antesDelCorte = LocalTime.now().isBefore(HORA_DE_CORTE);
            if (deuda > 0 && antesDelCorte) {
                log.warn("Cliente moroso con deuda pendiente: {}", deuda);
                contexto.rechazar("Cliente con deuda pendiente: $" + deuda);
            } else if (deuda > 0) {
                log.info("Cliente moroso fuera del horario de corte; se permite el pedido excepcionalmente");
            }
        }
    }
}
