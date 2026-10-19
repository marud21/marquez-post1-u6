# Post-contenido — Unidad 6: Antipatrones de Diseño

## Descripción
Repositorio del post-contenido de la Unidad 6 de Patrones de Diseño de Software. Un único proyecto
Spring Boot (`pedidos-service`) para la gestión de pedidos de un comercio electrónico, con dos partes:
diagnóstico y refactorización de un antipatrón combinado en `GestorPedidos`, y diagnóstico y
corrección de un segundo antipatrón introducido al hacer crecer el mismo proyecto.

## Cómo ejecutar
```
$ mvn spring-boot:run      # API en http://localhost:8080, consola H2 en /h2-console
$ mvn test                 # 25 pedidos de prueba (13 de la Parte 1 + 12 de campañas)
```
Ejemplo de pedido (cliente VIP con más de $1.000.000 → 15 %):
```
curl -X POST http://localhost:8080/api/pedidos -H "Content-Type: application/json" \
  -d '{"clienteId":1,"clienteEmail":"valentina@tienda.co","items":[{"productoId":2,"cantidad":5}]}'
```
Responde 201 con `{"confirmado":true,"pedidoId":...,"total":1264375.0}` o 422 con el motivo de rechazo.
Los datos semilla (`src/main/resources/data.sql`) incluyen un cliente por cada ruta:

| id | Cliente | Tipo | Para probar |
|---|---|---|---|
| 1 | Valentina Rojas | VIP | 5 % / 10 % / 15 % según el subtotal |
| 2 | Andrés Pérez | FRECUENTE | 11 pedidos previos → 8 % |
| 3 | Camila Duarte | FRECUENTE | 5 pedidos previos → 4 % |
| 4 | Jorge Mendoza | MOROSO | deuda de $150.000: rechazo antes de las 20:00 |
| 5 | Laura Gómez | ESTANDAR | sin descuento |
| 6 | Soluciones Andinas S.A.S. | ESTANDAR con NIT | campaña CORPORATIVO |

El producto 3 tiene stock 2 (stock insuficiente) y el producto 4 cuesta $1.000 (campaña VOLUMEN con
más de 20 unidades). BLACK_FRIDAY se activa con `promo.black-friday.activa=true` en
`application.properties`.

## Estructura final
```
src/main/java/com/tienda/pedidos/
├── controller/PedidoController          POST /api/pedidos
├── dto/                                 PedidoRequest, ItemPedido, ResultadoPedido
├── service/
│   ├── GestorPedidos                    orquestador delgado (@Transactional)
│   ├── NotificacionPedidoService        arma y envía el correo
│   └── EmailService / EmailServiceConsola
├── validacion/        Chain of Responsibility
│   ├── ValidadorPedido                  eslabón abstracto (corte anticipado)
│   ├── ValidadorStock ─► ValidadorCliente
│   └── ContextoPedido
├── descuento/         Strategy
│   ├── EstrategiaDescuento              DescuentoVip, DescuentoFrecuente, DescuentoEstandar
│   ├── SelectorEstrategiaDescuento      elige UNA por tipo de cliente
│   ├── CampanaDescuento                 DescuentoBlackFriday, DescuentoCorporativo, DescuentoVolumen
│   └── CalculadorDescuentoFinal         max(tipo de cliente, mejor campaña)
└── repository/        PedidoRepository, InventarioRepository, ClienteRepository, ProductoRepository
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
quedó en ~25 líneas que solo coordinan:

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

*Corrección sobre el código de referencia de la guía:* allí se escribe
`this.primerValidador = stock.encadenar(cliente);`. Como `encadenar()` devuelve el **siguiente**
eslabón, eso deja a `ValidadorCliente` como primer eslabón y `ValidadorStock` **nunca se ejecuta**.
Aquí la cadena se arma con `stock.encadenar(cliente)` y se guarda `primerValidador = stock`. La
prueba `stockInsuficienteSeRechazaSinTocarElInventario` habría detectado el error, porque con la
versión de la guía ese pedido se confirmaba.

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

### Parte 2 — Diagnóstico del crecimiento (antes de corregir)

> Las líneas citadas corresponden al commit `4f09508`
> (`feat: agregar 3 campanas de descuento como eslabones de la cadena de validacion`).

Mercadeo pidió tres campañas: BLACK_FRIDAY (25 % fijo), CORPORATIVO (10 % con NIT) y VOLUMEN (12 % con
más de 20 unidades). Se implementaron como `PromocionBlackFriday`, `PromocionCorporativo` y
`PromocionVolumen`, que **extienden `ValidadorPedido`**, y se engancharon a la cadena existente
(`GestorPedidos.java:44`):

```java
stock.encadenar(cliente).encadenar(blackFriday).encadenar(corporativo).encadenar(volumen);
```

**Antipatrón identificado: Golden Hammer.** Ya no es God Object ni Spaghetti Code: no hay una clase
que concentre todo ni anidamiento profundo. Lo que pasó es que se aplicó la herramienta que "ya
funcionó" en la Parte 1 a un problema que **no tiene la forma de una cadena**.

#### Evidencia 1 — No hay dependencia de orden entre las campañas
Las tres llaman a `contexto.aplicarDescuentoCampana(valor)` (`PromocionBlackFriday.java:16`,
`PromocionCorporativo.java:17`, `PromocionVolumen.java:12`). Ese método se queda con el máximo
(`ContextoPedido.java:28-30`):

```java
public void aplicarDescuentoCampana(double valor) {
    if (valor > this.descuentoCampana) this.descuentoCampana = valor; // el mayor descuento gana
}
```

`max` es conmutativo, así que **cualquier permutación** de los tres eslabones produce el mismo
descuento. Con los validadores pasa lo contrario: el orden es parte de la regla. Un pedido del
cliente 99 (inexistente) que pide 5 unidades del producto 3 (stock 2) se rechaza con
*"Stock insuficiente: producto 3"* porque `ValidadorStock` va primero. Si se invierte el orden, el
motivo cambia a *"Cliente no registrado"*. La cadena existe para modelar esa dependencia, y las
campañas no la tienen.

#### Evidencia 2 — Ninguna campaña usa la capacidad que justifica la cadena
`contexto.rechazar(...)` aparece solo en `ValidadorStock.java:26,33` y `ValidadorCliente.java:30,40`.
Las tres promociones **nunca rechazan** (el propio código lo dice en `PromocionBlackFriday.java:18`:
*"nunca rechaza -- este eslabon no valida nada"*), así que nunca usan el corte anticipado. Su contrato
real ("calcular un porcentaje") es el de `EstrategiaDescuento`, no el de `ValidadorPedido` ("decidir
si el pedido continúa o se rechaza"). Que una clase llamada *Validador* contenga cosas que no validan
es una violación de Liskov: no se pueden sustituir por un validador real sin cambiar el significado
de la cadena.

#### Evidencia 3 — La posición en la cadena importa por razones accidentales
Las promociones solo funcionan **si quedan después** de los validadores:
- Si `PromocionCorporativo` se pusiera antes de `ValidadorCliente`, un cliente inexistente haría
  fallar su `jdbcTemplate.queryForObject` (línea 14) con `EmptyResultDataAccessException`, en vez de
  rechazarse con *"Cliente no registrado"*.
- La cadena se ejecuta **antes** de calcular el subtotal (`GestorPedidos.java:57`, frente a las
  líneas 62–63), así que
  `contexto.getSubtotal()` vale `0` en todos los eslabones. Por eso `PromocionVolumen` recalcula las
  unidades desde el request (su comentario lo admite), y una campaña futura basada en el monto
  ("10 % si el subtotal supera $2.000.000") **no podría implementarse** como eslabón sin mover el
  cálculo de precio dentro de la validación.

#### Evidencia 4 — La regla de combinación quedó partida y oculta
La decisión "gana el mayor descuento" quedó dividida entre dos clases que no se conocen:
- `ContextoPedido.aplicarDescuentoCampana` (línea 29) toma el máximo **entre campañas**;
- `GestorPedidos.java:66` toma el máximo **entre ese valor y el del tipo de cliente**.

Si mercadeo pide que las campañas **se sumen**, habría que modificar el *contexto de validación*, y
la cadena no permite expresar "estas dos se suman y esta compite" sin ambigüedad. El estado mutable
compartido (`descuentoCampana`, línea 14) hace que el resultado dependa de que nadie más escriba ese
campo.

#### Evidencia 5 — La elección no se evaluó
No hay ningún criterio del problema que lleve a la cadena. El constructor de `GestorPedidos` pasó de
2 a 5 eslabones (línea 44) solo porque `encadenar()` ya existía. Las tres campañas tienen exactamente
la forma de `DescuentoVip` y `DescuentoFrecuente`: calculan un porcentaje a partir del pedido o del
cliente, sin orden y sin corte. La herramienta correcta **ya estaba en el proyecto** y no se usó.

### Parte 2 — Corrección aplicada

- Se **eliminaron** `PromocionBlackFriday`, `PromocionCorporativo`, `PromocionVolumen` y el campo
  `descuentoCampana` de `ContextoPedido`. `ValidadorPedido` vuelve a tener solo los dos eslabones que
  justifican la cadena: `ValidadorStock → ValidadorCliente`.
- Las campañas pasan a ser estrategias: `DescuentoBlackFriday`, `DescuentoCorporativo` y
  `DescuentoVolumen` implementan `CampanaDescuento`, que extiende `EstrategiaDescuento`.
  `DescuentoCorporativo` consulta el NIT a través de `ClienteRepository.nitDe()`, sin SQL embebido.
- `CalculadorDescuentoFinal` es el **único** lugar con la regla de combinación: el mayor entre la
  estrategia del tipo de cliente y la mejor campaña. Se ejecuta después de la validación y con el
  subtotal ya calculado, así que una campaña por monto ya es posible. `GestorPedidos` solo llama a
  `calculadorDescuento.calcular(contexto)`.

#### Decisión 5 — Strategy en vez de más eslabones de cadena
Las tres campañas calculan un porcentaje sin depender del orden de evaluación y sin necesitar cortar
el flujo del pedido. Esa es exactamente la forma de `EstrategiaDescuento`, no la de
`ValidadorPedido`. Se diferencian de las estrategias por tipo de cliente en un solo punto: de las
primeras se elige **una** (`SelectorEstrategiaDescuento`) y de las campañas se evalúan **todas**. Por
eso se separaron con la interfaz `CampanaDescuento`.

*Alternativa descartada: dejarlas en la cadena.* Es la causa del antipatrón diagnosticado. Reutiliza
una herramienta por familiaridad sin verificar que el problema tenga su forma, y deja las cinco
evidencias de arriba.

*Alternativa descartada: registrarlas en el mapa de `SelectorEstrategiaDescuento`.* El selector
devuelve una sola estrategia por tipo de cliente. Un cliente VIP con más de 20 unidades necesita
evaluar **a la vez** su estrategia VIP y la de volumen, y eso no cabe en un mapa `tipo → estrategia`.

*Ajuste frente a la guía:* la guía inyecta las tres campañas concretas en el constructor de
`CalculadorDescuentoFinal`. Aquí se inyecta `List<CampanaDescuento>`, así que una cuarta campaña es
solo un `@Component` nuevo, sin modificar el calculador (abierto/cerrado).

#### Decisión 6 — Eliminar, no comentar, el código descartado
Las tres clases y el campo se borraron (`git rm`), no se comentaron. Dejarlas "por si acaso" es
como nace un **Lava Flow**: nadie se atreve a borrarlas después porque ya no está claro si cumplen
alguna función. El historial queda en Git: la versión con eslabones está en los commits `4f09508` y
`dd73546`, y su eliminación en `98390e9`.

#### Comparación antes / después de la corrección
Las 25 pruebas se ejecutaron **sin cambios** sobre la versión Golden Hammer (commit `dd73546`) y
sobre la corregida (commit `98390e9`). `git diff --no-index` entre las salidas de ambas versiones no
reporta diferencias en ninguno de los tres archivos (`salida-pedidos.txt`, `salida-campanas.txt` y
`salida-black-friday.txt`).

```
=== Black Friday inactiva (promo.black-friday.activa=false) ===
C1 CORPORATIVO (NIT)                     | CONFIRMADO | descuento=10%  | total=214200.0
C2 VOLUMEN 25 unidades (ESTANDAR)        | CONFIRMADO | descuento=12%  | total=26180.0
C3 VOLUMEN 20 unidades (limite)          | CONFIRMADO | descuento=0%   | total=23800.0
C4 VIP 5% vs VOLUMEN 12%                 | CONFIRMADO | descuento=12%  | total=21991.2
C5 VIP 15% vs VOLUMEN 12%                | CONFIRMADO | descuento=15%  | total=1285616.5
C6 CORPORATIVO 10% vs VOLUMEN 12%        | CONFIRMADO | descuento=12%  | total=31416.0
C7 Black Friday inactiva (ESTANDAR)      | CONFIRMADO | descuento=0%   | total=119000.0
C8 VOLUMEN con stock insuficiente        | RECHAZADO  | Stock insuficiente: producto 3

=== Black Friday activa (promo.black-friday.activa=true) ===
B1 Black Friday (ESTANDAR)               | CONFIRMADO | descuento=25%  | total=178500.0
B2 Black Friday vs VIP 15%               | CONFIRMADO | descuento=25%  | total=1115625.0
B3 Black Friday vs CORPORATIVO + VOLUMEN | CONFIRMADO | descuento=25%  | total=26775.0
B4 Black Friday con cliente inexistente  | RECHAZADO  | Cliente no registrado
```

Los 13 pedidos de la Parte 1 también producen la misma salida que el `GestorPedidos` original del
commit `772c2f3`. El comportamiento observable se conservó a lo largo de las cuatro versiones:
original, refactorizada, Golden Hammer y corregida.

## Historial de commits
| Parte | Commit | Paso del proceso |
|---|---|---|
| 1 | `772c2f3` | Código de partida: todo en `procesarPedido()` |
| 1 | `986a824` | Pruebas de caracterización (línea base) |
| 1 | `72ea5fc` | Diagnóstico de God Object + Spaghetti Code |
| 1 | `428bdcb` | Validaciones → Chain of Responsibility, descuentos → Strategy |
| 1 | `dc2d0b5` | `GestorPedidos` como orquestador; se eliminan los parches de infraestructura |
| 1 | `b2665d0`, `580b2b7` | Justificación de decisiones y comparación antes/después |
| 2 | `4f09508` | Campañas agregadas como eslabones de la cadena (Golden Hammer) |
| 2 | `dd73546` | Pruebas de caracterización de las campañas |
| 2 | `eb72e1d` | Diagnóstico de Golden Hammer |
| 2 | `98390e9` | Campañas → `EstrategiaDescuento`; eslabones eliminados |

## Herramientas utilizadas
- Java 17, Spring Boot 3.2, Spring JDBC (`JdbcTemplate`), H2 Database, Maven
- JUnit 5 y Mockito (`mockStatic` para fijar la hora del día en las pruebas)
- VS Code, Git, GitHub

## Conclusiones
Diagnosticar antes de refactorizar cambió el resultado. Al citar líneas concretas apareció que el
anidamiento más profundo no estaba en el descuento sino en la regla de mora. Ejecutar el código de
partida, en vez de solo leerlo, destapó problemas que su diseño escondía: `CALL IDENTITY()` sin
transacción, `null` donde JdbcTemplate lanza excepción, y un reloj imposible de controlar.

La prueba de caracterización, escrita contra la API pública antes de tocar nada, fue lo que hizo
seguros los refactors. El mismo archivo pasó sobre cuatro versiones del código, y además habría
detectado el error de encadenamiento del código de referencia de la guía.

La Parte 2 mostró que un patrón bien aplicado no es una solución reutilizable por sí misma. Chain of
Responsibility era correcta para validaciones con orden y corte, y se volvió un Golden Hammer cuando
se usó para descuentos que no tenían ninguna de esas dos propiedades. La pregunta que distinguió
ambos casos no fue "¿qué patrón conozco?", sino "¿importa el orden y alguien debe poder detener el
flujo?".
