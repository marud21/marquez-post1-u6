-- Catálogo
INSERT INTO productos (id, nombre, precio) VALUES
    (1, 'Teclado mecánico', 100000),
    (2, 'Monitor 27"',      250000),
    (3, 'Mouse inalámbrico', 50000),
    (4, 'Cable USB-C',        1000);

INSERT INTO inventario (producto_id, stock) VALUES
    (1, 100),
    (2, 50),
    (3, 2),      -- stock bajo para el caso de stock insuficiente
    (4, 500);

-- Un cliente por cada ruta de negocio
INSERT INTO clientes (id, nombre, tipo_cliente) VALUES
    (1, 'Valentina Rojas', 'VIP'),
    (2, 'Andrés Pérez',    'FRECUENTE'),   -- 11 pedidos previos -> 8 %
    (3, 'Camila Duarte',   'FRECUENTE'),   --  5 pedidos previos -> 4 %
    (4, 'Jorge Mendoza',   'MOROSO'),      -- deuda pendiente de $150.000
    (5, 'Laura Gómez',     'ESTANDAR');

INSERT INTO clientes (id, nombre, tipo_cliente, nit) VALUES
    (6, 'Soluciones Andinas S.A.S.', 'ESTANDAR', '900123456-7');   -- cliente corporativo

INSERT INTO facturas (cliente_id, monto, pagada) VALUES
    (4, 150000, FALSE),
    (4,  50000, TRUE),
    (1,  80000, TRUE);

-- Historial de pedidos para los clientes FRECUENTE
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado)
    SELECT 2, 100000, 0, 19000, 119000, TIMESTAMP '2026-01-15 10:00:00', 'CONFIRMADO' FROM SYSTEM_RANGE(1, 11);
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado)
    SELECT 3, 100000, 0, 19000, 119000, TIMESTAMP '2026-02-10 10:00:00', 'CONFIRMADO' FROM SYSTEM_RANGE(1, 5);
