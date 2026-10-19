package com.tienda.pedidos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Arma y envía el correo de confirmación. Un fallo de envío no revierte el pedido. */
@Service
public class NotificacionPedidoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionPedidoService.class);

    private final EmailService emailService;

    public NotificacionPedidoService(EmailService emailService) {
        this.emailService = emailService;
    }

    public void notificarConfirmacion(String destinatario, Long pedidoId, double subtotal,
                                      double descuento, double impuesto, double total) {
        String asunto = "Confirmacion de pedido #" + pedidoId;
        String cuerpo = "Estimado cliente,\n\nSu pedido ha sido confirmado.\n"
                + "Subtotal: $" + subtotal + "\n"
                + (descuento > 0 ? "Descuento aplicado: " + (int) (descuento * 100) + "%\n" : "")
                + "Impuesto: $" + impuesto + "\nTotal: $" + total + "\n";
        try {
            emailService.enviar(destinatario, asunto, cuerpo);
        } catch (Exception e) {
            log.error("No se pudo enviar la notificacion del pedido {}: {}", pedidoId, e.getMessage());
        }
    }
}
