# Post-contenido — Unidad 6: Antipatrones de Diseño

## Descripción
Repositorio del post-contenido de la Unidad 6 de Patrones de Diseño de Software. Un único proyecto
Spring Boot (`pedidos-service`) para la gestión de pedidos de un comercio electrónico, con dos partes:
diagnóstico y refactorización de un antipatrón combinado en `GestorPedidos`, y diagnóstico y
corrección de un segundo antipatrón introducido al hacer crecer el mismo proyecto.

## Cómo ejecutar
```
$ mvn spring-boot:run
$ mvn test
```

## Decisiones de diseño

### Parte 1 — Diagnóstico de `GestorPedidos` (antes de refactorizar)

> Las líneas citadas corresponden a
> `src/main/java/com/tienda/pedidos/service/GestorPedidos.java` en el commit `772c2f3`, la versión
> original copiada del enunciado. El método `procesarPedido()` ocupa las líneas **28 a 133**.

**Antipatrones identificados: God Object y Spaghetti Code, combinados.**

#### Evidencia 1 — Seis responsabilidades en un solo método (God Object)
| Responsabilidad | Líneas | Razón para cambiar |
|---|---|---|
| Validación de items y stock | 32–44 | cambia la política de inventario (reservas, backorder) |
| Validación de cliente y mora, con excepción por horario | 47–65 | cambia la política de crédito o el horario de corte |
| Cálculo de subtotal | 68–73 | cambia el origen de precios (listas de precio, moneda) |
| Cálculo de descuento e impuesto | 76–96 | marketing agrega o modifica un tipo de cliente; cambia la tarifa del IVA |
| Persistencia del pedido, detalle e inventario | 99–113 | cambia el esquema o la base de datos |
| Notificación (armado del texto y envío) | 116–129 | cambia el formato del correo o el canal (SMS, push) |
| Registro (*logging*) disperso | 29, 33, 41, 50, 59, 62, 127, 131 | cambia la política de auditoría |

Son al menos **seis razones distintas para cambiar** la misma clase, lo que viola el SRP. Además
`GestorPedidos` es el único dueño de todas las tablas del sistema: `inventario`, `clientes`,
`facturas`, `productos`, `pedidos` y `detalle_pedido`. Cualquier cambio en cualquiera de ellas obliga
a abrir este archivo.

#### Evidencia 2 — Flujo enredado y anidamiento (Spaghetti Code)
- **Validación de mora (líneas 49–65): 3 niveles de condicionales anidados** dentro del método,
  con salidas tempranas en dos niveles distintos:
  ```java
  } else if (tipoCliente.equals("MOROSO")) {              // nivel 1  (l. 52)
      ...
      if (deudaPendiente != null && deudaPendiente > 0) {  // nivel 2  (l. 56)
          LocalTime ahora = LocalTime.now();
          if (ahora.isBefore(LocalTime.of(20, 0))) {       // nivel 3  (l. 58)
              return ResultadoPedido.rechazado(...);       // salida temprana
          } else { log.info(...); }                        // rama que solo registra
  ```
- **Cálculo de descuento (líneas 77–93): 2 niveles de anidamiento** (tipo de cliente → rango de monto
  o de pedidos previos), con un `else` implícito para cualquier otro tipo. MOROSO y ESTANDAR terminan
  en `descuento = 0` sin que el código lo diga.
- **Cinco `return` en cuatro puntos distintos** (líneas 34, 42, 51, 60 y 132). Para saber en qué
  estado queda la base de datos ante cada rechazo hay que seguir el método completo.
- **Una variable de control cruza todo el método:** `tipoCliente` se lee en la línea 47 y se vuelve a
  evaluar en las líneas 52, 77 y 85. La validación y el descuento quedan acoplados por una variable
  local.

#### Evidencia 3 — Cuatro niveles de abstracción mezclados en la misma secuencia
En el mismo método conviven:
- **SQL:** 9 sentencias distintas, en las líneas 38, 48, 54, 71, 87, 100, 104, 108 y 111;
- **reglas de negocio:** los umbrales `1_000_000`, `500_000`, `10` y `3`, y la hora de corte `20:00`;
- **aritmética fiscal:** el IVA de `0.19` en las líneas 95–96;
- **formato de texto** de un correo, con `StringBuilder`, en las líneas 116–123.

Por ejemplo, la línea 72 (`subtotal += precioUnitario * ...`) depende de una consulta SQL en la línea
70 que ocurre *dentro* del cálculo de precio. Un pedido de *n* items ejecuta **4n + 3** consultas
(**4n + 4** si el cliente es MOROSO o FRECUENTE), una por item en tres bucles distintos (líneas 36,
69 y 106).

#### Evidencia 4 — Costo de agregar un tipo de cliente
Agregar, por ejemplo, un tipo `EMPLEADO` con 12 % obliga a **modificar el bloque de las líneas 76–93**
insertando otra rama `else if` dentro del método que también valida, persiste y notifica. Para
verificar ese cambio hay que ejecutar el método completo: con base de datos, inventario y correo.
Ninguna regla de descuento se puede probar de forma aislada, porque no existe como unidad.

#### Hallazgos al ejecutar el código de partida
Ejecutar el código tal como viene en el enunciado reveló tres problemas que el diseño escondía. Se
resolvieron **sin modificar `GestorPedidos`**, porque la guía pide copiarlo exactamente:

1. **`CALL IDENTITY()` (línea 104) no existe en H2 2.x**, la versión que trae Spring Boot 3.2. Se
   habilitó con `MODE=LEGACY` en la URL de conexión. Además `IDENTITY()` devuelve el último id **de la
   sesión**: sin transacción explícita (línea 98), el `INSERT` de la línea 99 y la consulta de la línea
   104 pueden usar conexiones distintas del pool y devolver el id de otro pedido.
2. **`queryForObject` lanza `EmptyResultDataAccessException` cuando no hay filas**, pero las líneas
   40 y 49 asumen que devuelve `null`. Con un cliente inexistente el método lanzaba una excepción en
   lugar de rechazar el pedido. Se agregó un adaptador de infraestructura
   (`config/JdbcTemplateNuloSiVacio`) para que esa suposición sea válida y el comportamiento original
   se pueda caracterizar.
3. **`LocalTime.now()` (línea 57) es una dependencia oculta del reloj.** La regla de mora no se puede
   probar de forma determinista. Las pruebas fijan la hora con `Mockito.mockStatic`.

A esto se suma que la **validación de stock (líneas 37–43) y el descuento de inventario (líneas
110–112) no comparten transacción**. Dos pedidos simultáneos podrían pasar ambos la validación y
dejar el stock en negativo, y una falla en mitad del bucle de la línea 106 deja el pedido guardado
con el inventario descontado solo en parte.

#### Línea base de comportamiento
`GestorPedidosTest` (commit `986a824`) caracteriza el comportamiento observable antes de tocar el
código. Cubre 13 pedidos:
- las rutas de validación: sin items, stock insuficiente, cliente inexistente, y moroso dentro y fuera
  del horario de corte;
- todos los rangos de descuento VIP y FRECUENTE, y el cliente ESTANDAR;
- los efectos de un pedido confirmado: inventario, detalle y correo exacto.

La prueba usa solo la API pública `procesarPedido()`, así que el mismo archivo se ejecuta sin cambios
después de refactorizar.
