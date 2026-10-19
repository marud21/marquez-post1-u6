package com.tienda.pedidos.service;

/** Envío de correos. La implementación por defecto imprime en consola (sin servidor SMTP). */
public interface EmailService {

    void enviar(String destinatario, String asunto, String cuerpo);
}
