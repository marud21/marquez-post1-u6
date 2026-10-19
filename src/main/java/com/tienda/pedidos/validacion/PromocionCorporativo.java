package com.tienda.pedidos.validacion;

// Nuevo eslabon: cliente corporativo
@org.springframework.stereotype.Component
public class PromocionCorporativo extends ValidadorPedido {
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public PromocionCorporativo(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void ejecutar(ContextoPedido contexto) {
        String nit = jdbcTemplate.queryForObject(
            "SELECT nit FROM clientes WHERE id = ?", String.class, contexto.getRequest().getClienteId());
        if (nit != null && !nit.isBlank()) {
            contexto.aplicarDescuentoCampana(0.10);
        }
    }
}
