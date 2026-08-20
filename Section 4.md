## Punto 4.1 — SQL Avanzado Multi-Motor

---
A

**PostgreSQL**
```sql
WITH recent_orders AS (
    SELECT
        o.customer_id,
        o.id    AS order_id,
        o.total
    FROM orders o
    WHERE o.created_at >= NOW() - INTERVAL '30 days'
      AND o.status <> 'CANCELLED'
),
customer_aggregates AS (
    SELECT
        customer_id,
        SUM(total)                   AS total_spent,
        COUNT(order_id)              AS order_count,
        SUM(total) / COUNT(order_id) AS avg_ticket
    FROM recent_orders
    GROUP BY customer_id
),
ranked_customers AS (
    SELECT
        ca.*,
        RANK() OVER (ORDER BY ca.total_spent DESC) AS spend_rank
    FROM customer_aggregates ca
)
SELECT
    c.id                     AS customer_id,
    c.name                   AS customer_name,
    c.email,
    rc.order_count,
    rc.total_spent,
    ROUND(rc.avg_ticket, 2)  AS avg_ticket,
    rc.spend_rank
FROM ranked_customers rc
JOIN customers c ON c.id = rc.customer_id
WHERE rc.spend_rank <= 5
ORDER BY rc.spend_rank;
```

**OracleSQL**
```sql
WITH recent_orders AS (
    SELECT
        o.customer_id,
        o.id    AS order_id,
        o.total
    FROM orders o
    WHERE o.created_at >= SYSDATE - 30
      AND o.status <> 'CANCELLED'
),
customer_aggregates AS (
    SELECT
        customer_id,
        SUM(total)                   AS total_spent,
        COUNT(order_id)              AS order_count,
        SUM(total) / COUNT(order_id) AS avg_ticket
    FROM recent_orders
    GROUP BY customer_id
),
ranked_customers AS (
    SELECT
        ca.*,
        RANK() OVER (ORDER BY ca.total_spent DESC) AS spend_rank
    FROM customer_aggregates ca
)
SELECT
    c.id                     AS customer_id,
    c.name                   AS customer_name,
    c.email,
    rc.order_count,
    rc.total_spent,
    ROUND(rc.avg_ticket, 2)  AS avg_ticket,
    rc.spend_rank
FROM ranked_customers rc
JOIN customers c ON c.id = rc.customer_id
WHERE rc.spend_rank <= 5
ORDER BY rc.spend_rank;
```

---

B

**PostgreSQL**
```sql
WITH last_calendar_month AS (
    SELECT
        date_trunc('month', NOW() - INTERVAL '1 month')::date AS month_start,
        date_trunc('month', NOW())::date                       AS month_end  -- exclusivo
),
product_sales AS (
    SELECT
        oi.product_id,
        SUM(oi.quantity) AS units_sold
    FROM order_items oi
    JOIN orders o ON o.id = oi.order_id
    CROSS JOIN last_calendar_month lcm
    WHERE o.created_at >= lcm.month_start
      AND o.created_at <  lcm.month_end
      AND o.status <> 'CANCELLED'
    GROUP BY oi.product_id
)
SELECT
    p.id,
    p.name,
    p.category,
    p.stock,
    ps.units_sold
FROM products p
JOIN product_sales ps ON ps.product_id = p.id
WHERE p.stock < 10
  AND ps.units_sold > 50
ORDER BY ps.units_sold DESC;
```

**OracleSQL**
```sql
WITH last_calendar_month AS (
    SELECT
        TRUNC(ADD_MONTHS(SYSDATE, -1), 'MM') AS month_start,
        TRUNC(SYSDATE, 'MM')                 AS month_end  -- exclusivo
    FROM dual
),
product_sales AS (
    SELECT
        oi.product_id,
        SUM(oi.quantity) AS units_sold
    FROM order_items oi
    JOIN orders o ON o.id = oi.order_id
    CROSS JOIN last_calendar_month lcm
    WHERE o.created_at >= lcm.month_start
      AND o.created_at <  lcm.month_end
      AND o.status <> 'CANCELLED'
    GROUP BY oi.product_id
)
SELECT
    p.id,
    p.name,
    p.category,
    p.stock,
    ps.units_sold
FROM products p
JOIN product_sales ps ON ps.product_id = p.id
WHERE p.stock < 10
  AND ps.units_sold > 50
ORDER BY ps.units_sold DESC;
```

C

**PostgreSQL** 
```sql
WITH date_spine AS (
    SELECT generate_series(
               CURRENT_DATE - INTERVAL '6 days',
               CURRENT_DATE,
               INTERVAL '1 day'
           )::date AS sales_date
),
categories AS (
    SELECT DISTINCT category FROM products
),
calendar AS (
    SELECT ds.sales_date, c.category
    FROM date_spine ds
    CROSS JOIN categories c
),
daily_sales AS (
    SELECT
        o.created_at::date               AS sales_date,
        p.category,
        SUM(oi.quantity)                 AS units_sold,
        SUM(oi.quantity * oi.unit_price) AS revenue
    FROM orders o
    JOIN order_items oi ON oi.order_id = o.id
    JOIN products p     ON p.id = oi.product_id
    WHERE o.created_at >= CURRENT_DATE - INTERVAL '6 days'
      AND o.status <> 'CANCELLED'
    GROUP BY o.created_at::date, p.category
)
SELECT
    cal.sales_date,
    cal.category,
    COALESCE(ds.units_sold, 0) AS units_sold,
    COALESCE(ds.revenue, 0)    AS revenue
FROM calendar cal
LEFT JOIN daily_sales ds
       ON ds.sales_date = cal.sales_date
      AND ds.category   = cal.category
ORDER BY cal.sales_date, cal.category;
```

**OracleSQL** 

```sql
WITH date_spine AS (
    SELECT TRUNC(SYSDATE) - LEVEL + 1 AS sales_date
    FROM dual
    CONNECT BY LEVEL <= 7
),
categories AS (
    SELECT DISTINCT category FROM products
),
calendar AS (
    SELECT ds.sales_date, c.category
    FROM date_spine ds
    CROSS JOIN categories c
),
daily_sales AS (
    SELECT
        TRUNC(o.created_at)              AS sales_date,
        p.category,
        SUM(oi.quantity)                 AS units_sold,
        SUM(oi.quantity * oi.unit_price) AS revenue
    FROM orders o
    JOIN order_items oi ON oi.order_id = o.id
    JOIN products p     ON p.id = oi.product_id
    WHERE o.created_at >= TRUNC(SYSDATE) - 6
      AND o.status <> 'CANCELLED'
    GROUP BY TRUNC(o.created_at), p.category
)
SELECT
    cal.sales_date,
    cal.category,
    COALESCE(ds.units_sold, 0) AS units_sold,
    COALESCE(ds.revenue, 0)    AS revenue
FROM calendar cal
LEFT JOIN daily_sales ds
       ON ds.sales_date = cal.sales_date
      AND ds.category   = cal.category
ORDER BY cal.sales_date, cal.category;
```


Yo crearia indices para optimizar las consultas.

```sql
CREATE INDEX idx_orders_created_at        ON orders (created_at);
CREATE INDEX idx_orders_customer_id       ON orders (customer_id);
CREATE INDEX idx_order_items_order_id     ON order_items (order_id);
CREATE INDEX idx_order_items_product_id   ON order_items (product_id);
CREATE INDEX idx_products_category_stock  ON products (category, stock);
```

---

## Punto 4.2 

### El problema en números

10,000 peticiones/min ≈ 166 req/seg contra `GET /products/{id}`, pero los datos (precio, descripción) cambian **como máximo una vez por hora**. Sin caché, casi el 100% de esas 166 req/seg golpean la base de datos por datos que no cambiaron desde la última lectura — trabajo desperdiciado. El objetivo de Redis no es "hacerlo más rápido" en abstracto, es explotar que la tasa de lectura (166/seg) es órdenes de magnitud mayor que la tasa de escritura (≤1/hora).

### Estrategia: abstracción declarativa de Spring (`@Cacheable`/`@CachePut`/`@CacheEvict`)

Prefiero la abstracción de Spring Cache sobre un cliente Redis directo (Jedis/Lettuce a mano) por una razón concreta: la lógica de negocio (`ProductService`) no debería saber que existe Redis. Si mañana cambio Redis por Caffeine local o por Memcached, el código de negocio no se toca — solo la configuración del `CacheManager`. Usar Jedis/Lettuce directamente dentro del service acopla la capa de negocio a la tecnología de caché, el mismo error de diseño que vimos en la Sección 1 con el `OrderService` (mezclar responsabilidades que no le corresponden a esa clase).

Spring Boot con `spring-boot-starter-data-redis` usa **Lettuce** como cliente por defecto (no Jedis) — es la elección correcta acá: Lettuce es no bloqueante, basado en Netty, y una sola conexión soporta múltiples peticiones concurrentes de forma segura entre hilos. Jedis, en cambio, requiere un pool de conexiones (una por hilo activo), lo cual bajo 166 req/seg concurrentes exige dimensionar y gestionar ese pool cuidadosamente.

```java
// build.gradle
implementation 'org.springframework.boot:spring-boot-starter-data-redis' // trae Lettuce
implementation 'org.springframework.boot:spring-boot-starter-cache'
```

```java
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheManagerBuilderCustomizer productsCacheCustomizer() {
        RedisCacheConfiguration productsConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return builder -> builder.withCacheConfiguration("products", productsConfig);
    }
    
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Fallo leyendo Redis, cae a la base de datos. key={}", key, ex);
            }
            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("Fallo escribiendo en Redis. key={}", key, ex);
            }
            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Fallo invalidando Redis. key={}", key, ex);
            }
            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) { }
        };
    }
}
```

```java
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    @Cacheable(value = "products", key = "#id")
    public ProductResponse findById(Long id) {
        return productMapper.toResponse(findProductOrThrow(id));
    }
    
    @CachePut(value = "products", key = "#id")
    public ProductResponse update(Long id, ProductRequest request) { ... }

    @CacheEvict(value = "products", key = "#id")
    public void delete(Long id) { ... }
}
```

**Nota de diseño**: `stock` cambia con cada compra (mucho más seguido que precio/descripción), así que si `ProductResponse` incluye `stock`, cualquier método que lo modifique (como `purchaseStock`) también necesitaría `@CacheEvict` sobre esa misma key — de lo contrario, el caché serviría un stock desactualizado hasta que expire el TTL. La solución más limpia sería **no cachear `stock` junto con precio/descripción**: separar el detalle "mayormente estático" (cacheable, TTL largo) del stock (altamente dinámico, siempre leído en vivo o con un TTL de segundos), porque ambos campos tienen requisitos de frescura completamente distintos aunque vivan en la misma entidad.

### ¿Qué TTL, y con qué lógica de negocio?

**10 minutos, no 1 hora.** La razón: el TTL en este diseño no es el mecanismo principal de invalidación — lo es el `@CachePut`/`@CacheEvict` en el camino de escritura, que actualiza el caché en el mismo instante en que cambia el dato, sin esperar ningún TTL. El TTL es la **red de seguridad** para cuando la invalidación activa falla: una escritura directa a la base de datos que se salta la aplicación, un bug, o — en un despliegue multi-instancia — un nodo que se cae justo antes de propagar la invalidación.

Si le confías el TTL completo a la ventana de negocio (1 hora) y la invalidación activa falla por cualquier razón, el peor caso de inconsistencia es de hasta 1 hora — inaceptable para precios. Con TTL=10min, el peor caso baja a 10 minutos incluso si todo lo demás falla, mientras se sigue absorbiendo la gran mayoría de las 166 req/seg directamente desde Redis en el caso normal.

### Cache Stampede: qué es y cómo lo mitigo

**El fenómeno**: cuando una key "caliente" (un producto muy visitado) expira, y en ese mismo instante llegan cientos de peticiones concurrentes, todas ven un cache-miss simultáneo y todas golpean la base de datos al mismo tiempo para reconstruir el mismo valor. Bajo 166 req/seg sobre un catálogo con productos desiguales en popularidad (el típico "producto estrella" del Cyber-Day), esto puede tumbar la base de datos justo cuando más tráfico hay.

Lo mitigo con tres mecanismos combinados, del más simple al más robusto:

**1. Jitter en el TTL** — en vez de un TTL fijo de 10 minutos para todas las keys, agrego una variación aleatoria pequeña (`10min ± 60s`), para que un lote de productos cacheados al mismo tiempo (por ejemplo tras un deploy) no expire todo junto:
```java
Duration ttl = Duration.ofSeconds(600 + ThreadLocalRandom.current().nextInt(-60, 60));
```

**2. Lock distribuido de reconstrucción (mutex por key)** — antes de recalcular un valor tras un cache-miss, intento adquirir un lock corto en Redis con `SET key value NX PX 3000`. Solo el request que consigue el lock va a la base de datos; el resto espera brevemente y reintenta leer el caché en vez de ir todos a la vez a la base de datos:
```java
Boolean acquired = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, "1", Duration.ofSeconds(3));
if (Boolean.TRUE.equals(acquired)) {
    ProductResponse fresh = loadFromDatabaseAndCache(id);
    redisTemplate.delete(lockKey);
    return fresh;
} else {
    Thread.sleep(50); // backoff corto
    return findFromCacheOrRetry(id);
}
```

**3. Caché local (L1) delante de Redis (L2)** — dado que la propia instancia recibe múltiples hilos pidiendo el mismo producto en el mismo milisegundo, una caché en memoria (Caffeine) con TTL muy corto (segundos) delante de Redis reduce los round-trips a Redis y mitiga el stampede a nivel de instancia: si 50 hilos piden el mismo producto en la misma ráfaga, solo el primero golpea Redis/DB — el resto se sirve de la entrada L1 recién poblada.

Para el volumen descrito, empezaría solo con **jitter + `@Cacheable`** — es la solución más simple que cubre el caso común — y agregaría el lock distribuido específicamente si el monitoreo muestra picos de carga en la base de datos correlacionados con expiraciones de productos populares.