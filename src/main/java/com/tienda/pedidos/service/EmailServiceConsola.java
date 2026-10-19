package com.tienda.pedidos.service;

import org.springframework.stereotype.Service;

@Service
public class EmailServiceConsola implements EmailService {

    @Override
    public void enviar(String destinatario, String asunto, String cuerpo) {
        System.out.println("[EMAIL -> " + destinatario + "] " + asunto + "\n" + cuerpo);
    }
}
