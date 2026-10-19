package com.tienda.pedidos;

import com.tienda.pedidos.service.EmailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

/** EmailService de prueba: guarda los correos en vez de imprimirlos, para poder verificarlos. */
@TestConfiguration
public class CorreosRegistrados {

    public record Correo(String destinatario, String asunto, String cuerpo) { }

    public static class Registro implements EmailService {
        private final List<Correo> enviados = new ArrayList<>();

        @Override
        public void enviar(String destinatario, String asunto, String cuerpo) {
            enviados.add(new Correo(destinatario, asunto, cuerpo));
        }

        public Correo ultimo() {
            return enviados.isEmpty() ? null : enviados.get(enviados.size() - 1);
        }

        public int cantidad() {
            return enviados.size();
        }
    }

    @Bean
    @Primary
    public Registro registroCorreos() {
        return new Registro();
    }
}
