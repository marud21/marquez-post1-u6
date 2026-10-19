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

### Parte 1 — Refactorización aplicada

`GestorPedidos` (106 líneas en un método) se dividió en cuatro capas cohesivas. `procesarPedido()`
quedó en ~20 líneas que solo coordinan:

```
GestorPedidos (orquestador, @Transactional)
 ├─ validacion/  Chain of Responsibility   ValidadorStock ──► ValidadorCliente
 ├─ descuento/   Strategy                  SelectorEstrategiaDescuento ─► DescuentoVip | DescuentoFrecuente | DescuentoEstandar
 ├─ repository/  persistencia              PedidoRepository, InventarioRepository, ClienteRepository, ProductoRepository
 └─ service/     notificación              NotificacionPedidoService ─► EmailService
```

| Responsabilidad original (líneas) | Ahora vive en |
|---|---|
| Items y stock (32–44) | `validacion/ValidadorStock` + `InventarioRepository.stockDe` |
| Cliente y mora (47–65) | `validacion/ValidadorCliente` + `ClienteRepository` |
| Subtotal (68–73) | `GestorPedidos.calcularSubtotal` + `ProductoRepository.precioDe` |
| Descuento (76–93) | `descuento/` — una clase por tipo de cliente, elegida por `SelectorEstrategiaDescuento` |
| Persistencia (99–113) | `repository/PedidoRepository.guardar` (transaccional) |
| Notificación (116–129) | `service/NotificacionPedidoService` |

#### Decisión 1 — Validaciones con Chain of Responsibility
Las validaciones tienen una **dependencia real de orden y de corte anticipado**. Si
`ValidadorStock` rechaza, `ValidadorCliente` no debe ejecutarse ni consultar la mora. El código
original lo expresaba con `return` intercalados (líneas 34, 42, 51 y 60). En la cadena,
`ValidadorPedido.validar()` solo delega al siguiente si el contexto no fue rechazado, y cada eslabón
tiene un único motivo de rechazo.

*Alternativa descartada:* un método `validarTodo()` que recorra una
`List<Predicate<ContextoPedido>>`. Evalúa todos los predicados aunque el primero haya fallado, y un
predicado booleano no puede **escribir en el contexto**. `ValidadorCliente` necesita guardar el
`tipoCliente` que después usa el Strategy, y con una lista de predicados habría que volver a
consultarlo.

#### Decisión 2 — Descuento con Strategy, no como otro eslabón de la cadena
Las reglas de descuento **no dependen de un orden ni necesitan cortar el flujo**: siempre se aplica
exactamente una, según el tipo de cliente. El `if/else` anidado de las líneas 77–93 se reemplaza por
un `Map` en `SelectorEstrategiaDescuento` (Strategy + Factory simple), con un único punto de
decisión. MOROSO y ESTANDAR, que antes caían en un `else` implícito, ahora usan `DescuentoEstandar`
de forma explícita. Agregar un tipo `EMPLEADO` es crear una clase y registrarla en el mapa, sin abrir
`GestorPedidos`.

*Alternativa descartada:* modelar cada descuento como un eslabón más de la cadena. Habría obligado
a inventar un mecanismo para que solo un eslabón fijara el descuento (por ejemplo, "no sobrescribir
si ya hay uno"). Además mezcla dos contratos distintos: "¿el pedido continúa?" y "¿qué porcentaje
aplica?".

#### Decisión 3 — El SQL vive en repositorios, no en validadores ni estrategias
La guía de referencia dejaba `jdbcTemplate.queryForObject(...)` dentro de `ValidadorStock`,
`ValidadorCliente` y `DescuentoFrecuente`. Así cada clase nueva repetiría, en pequeño, la Evidencia 3:
SQL y regla de negocio en la misma línea. Por eso las consultas se aislaron en `repository/`, con
métodos que expresan intención (`stockDe`, `tipoDe`, `deudaPendiente`, `pedidosPrevios`,
`precioDe`) y devuelven `Optional` cuando el dato puede no existir. Con eso dejaron de hacer falta
los dos parches que exigía el código original, y ambos se eliminaron:
- el adaptador `JdbcTemplateNuloSiVacio`;
- el modo `LEGACY` de H2.

#### Decisión 4 — Persistencia transaccional con id generado
`PedidoRepository.guardar()` es `@Transactional` y obtiene el id con `KeyHolder` desde el propio
`INSERT`, en lugar de `CALL IDENTITY()`. `GestorPedidos.procesarPedido()` también es
`@Transactional`, así que la validación de stock y el descuento de inventario ocurren en la misma
transacción. Si falla un item a mitad del bucle, el pedido no queda guardado a medias.

#### Comparación antes / después
La salida de `GestorPedidosTest` se escribe en `target/salida-pedidos.txt`. Se ejecutó el **mismo
archivo de prueba** sobre el código original (commit `986a824`) y sobre el refactorizado (commit
`dc2d0b5`). `git diff --no-index` entre ambas salidas no reporta diferencias:

```
01 sin items                             | RECHAZADO  | El pedido no contiene items
02 stock insuficiente                    | RECHAZADO  | Stock insuficiente: producto 3
03 cliente inexistente                   | RECHAZADO  | Cliente no registrado
04 moroso 10:00 (dentro del corte)       | RECHAZADO  | Cliente con deuda pendiente: $150000.0
05 moroso 21:30 (fuera del corte)        | CONFIRMADO | descuento=0%   | total=238000.0
06 VIP > 1.000.000                       | CONFIRMADO | descuento=15%  | total=1264375.0
07 VIP 500.000 - 1.000.000               | CONFIRMADO | descuento=10%  | total=642600.0
08 VIP <= 500.000                        | CONFIRMADO | descuento=5%   | total=226100.0
09 FRECUENTE > 10 pedidos                | CONFIRMADO | descuento=8%   | total=328440.0
10 FRECUENTE 4 - 10 pedidos              | CONFIRMADO | descuento=4%   | total=125664.0
11 ESTANDAR                              | CONFIRMADO | descuento=0%   | total=297500.0
12 efectos (VIP, 2 productos)            | CONFIRMADO | descuento=10%  | total=696150.0
13 correo sin descuento (ESTANDAR)       | CONFIRMADO | descuento=0%   | total=119000.0
```

Además de esta tabla, las 13 pruebas verifican en ambas versiones:
- el texto exacto del correo;
- el inventario descontado;
- las filas de `detalle_pedido`;
- que un pedido rechazado no envía correo.
