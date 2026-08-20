## Punto 5.1 — Arquitectura de Despliegue Cloud Native e Inmutabilidad

Los archivos reales de este punto viven en [`deploy/`](deploy/): [`deploy/Dockerfile`](deploy/Dockerfile), [`deploy/.dockerignore`](deploy/.dockerignore) y [`deploy/github-actions-deploy.yml`](deploy/github-actions-deploy.yml). No tocan la app (`build.gradle`, `application.yaml`, el código Java quedan exactamente igual) — son artefactos de despliegue nuevos e independientes.

---

### A) ¿Qué servicio de cómputo elegirías: Cloud Run, GKE o App Engine?

**Cloud Run.** La justifico comparando los tres en los mismos tres ejes que pide el enunciado:

**Costo**
- **Cloud Run**: cobra por vCPU-segundo y GiB-segundo, solo mientras se procesa una request, en incrementos de ~100ms. Escala a **cero** instancias cuando no hay tráfico — costo cero fuera de horario, salvo que configures `min-instances > 0` por latencia.
- **GKE**: pagas los nodos de Compute Engine (modo Standard) de forma continua, tengan tráfico o no, más una cuota de gestión del clúster. Autopilot cobra por recursos de pod en vez de por nodo (más eficiente), pero sigue sin escalado-a-cero real del clúster y mantiene la misma cuota de gestión.
- **App Engine Flexible** (el modo que sí corre un contenedor arbitrario, no Standard con su sandbox limitado): corre sobre VMs administradas de Compute Engine y **no** escala a cero — mantiene al menos una instancia arriba, facturada por hora de instancia, tengas tráfico o no.

**Autoescalado**
- **Cloud Run**: escala por **concurrencia de requests por instancia** (default 80, configurable hasta 1000) — es la señal correcta para una API REST, nativo, sin configurar nada extra.
- **GKE**: dos capas independientes que hay que coordinar manualmente — el Horizontal Pod Autoscaler (réplicas de pod según CPU/memoria/métrica custom) y el Cluster Autoscaler (tamaño del node pool según pods pendientes). Más poderoso, pero con muchas más piezas móviles.
- **App Engine Flexible**: escala agregando/quitando VMs completas — más lento por el tiempo de arranque de una VM comparado con escalar a nivel de contenedor.

**Sobrecarga operacional**
- **Cloud Run**: no hay servidores, node pools, parcheo de SO ni upgrades de clúster que gestionar — `gcloud run deploy` es prácticamente toda la superficie operacional.
- **GKE**: incluso en Autopilot exige literacia de Kubernetes (manifiestos/Helm, RBAC, Services/Ingress, ventanas de upgrade del clúster). Standard suma selección de machine-type y parcheo de nodos.
- **App Engine Flexible**: es la opción con menos inversión reciente de las tres, deploys más lentos (build de imagen de VM).

**Veredicto**: para "microservicios contenedorizados" — plural, stateless, disparados por HTTP, que es exactamente el enunciado — Cloud Run gana en los tres ejes al mismo tiempo. GKE se justificaría solo si esto evoluciona a muchos servicios interdependientes que necesiten un service mesh compartido, inyección de sidecars (Istio/Anthos), CNI custom o cargas con estado — nada de eso aplica a esta API REST de Spring Boot respaldada por una base de datos. App Engine Flexible queda dominado por Cloud Run en los tres ejes para este caso puntual: mismo modelo basado en contenedor, pero sin escalado a cero y con deploys más lentos — no hay escenario aquí donde gane.

---

### B) Dockerfile optimizado para producción

Ver [`deploy/Dockerfile`](deploy/Dockerfile) completo. Decisiones clave:

**Multi-stage build** — dos etapas, `build` (JDK) y `runtime` (JRE), para que el compilador, el wrapper de Gradle, y todo el árbol de dependencias de compilación jamás lleguen a la imagen final que corre en producción.

**Orden de capas para maximizar cache**: copio `gradlew`/`gradle/`/`build.gradle`/`settings.gradle` y resuelvo dependencias **antes** de copiar `src/`. Así, editar código de aplicación no invalida la descarga de dependencias (la capa más pesada y lenta) — solo cambiar `build.gradle` la invalida. Los tests se saltan explícitamente en la imagen (`-x test`) porque ya corren como su propio paso en CI (parte D) — repetirlos dentro del build de la imagen sería trabajo duplicado.

**Jar por capas (layered jar)**: en vez de copiar el fat jar completo como un solo bloque, lo extraigo con `java -Djarmode=tools -jar app.jar extract --layers` (verifiqué contra el jar real ya compilado de este proyecto que el jarmode se llama `tools` en Spring Boot 4.1.0, no `layertools` como en versiones más viejas) y copio cada capa por separado en la etapa runtime, de la que menos cambia a la que más cambia. Resultado concreto: si solo cambia código de aplicación, solo se reconstruye/repuja la capa `application` — la capa `dependencies` (la más pesada) se queda cacheada tanto en el build local como en Artifact Registry.

**Usuario no-root**, con UID/GID fijos en vez de dejar que la imagen asigne uno dinámico:
```dockerfile
RUN addgroup --system --gid 1001 spring \
    && adduser --system --uid 1001 --ingroup spring --no-create-home spring
USER spring:spring
```
UID/GID fijos hacen que la propiedad de archivos vía `--chown=spring:spring` sea reproducible entre builds.

**Superficie de ataque**: uso `eclipse-temurin:21-jre-jammy` (JRE, no JDK) como base de runtime. La alternativa más agresiva —y la que menciono explícitamente como la opción "correcta" si la prioridad es minimizar CVEs al máximo— es `gcr.io/distroless/java21-debian12`: sin shell, sin gestor de paquetes, sin `apt`/`curl`. El trade-off real: `docker exec sh` deja de ser posible para debugging, y un `HEALTHCHECK` basado en shell no funciona ahí (de todos modos, Cloud Run no lee la instrucción `HEALTHCHECK` del Dockerfile — usa su propia configuración de probes de startup/liveness, así que en Cloud Run específicamente esto es solo higiene para `docker run`/`docker-compose` local, no algo que la plataforma consuma).

**`ENTRYPOINT`**: `org.springframework.boot.loader.launch.JarLauncher` — verifiqué el `Main-Class` exacto contra el `META-INF/MANIFEST.MF` del jar ya compilado de este proyecto (paquete `launch`, movido ahí desde `org.springframework.boot.loader.JarLauncher` en versiones pre-3.2). No lo asumí de memoria porque Boot 4.1.0 ya nos movió paquetes similares dos veces en este proyecto (`@WebMvcTest`, `RedisCacheManagerBuilderCustomizer`).

**Nota sobre el puerto**: expongo `8080`, que hoy coincide con el default de Cloud Run (`$PORT`) y el default de Spring Boot (`server.port`) — pero es una coincidencia, no un mapeo automático. Cloud Run inyecta `$PORT` como variable de entorno; Spring Boot no la lee automáticamente. La forma portable sería `server.port: ${PORT:8080}` en `application.yaml` — lo documento acá como observación, sin tocar el `application.yaml` real del proyecto (eso es cambio de app, no de despliegue).

**Flags JVM conscientes del contenedor**: `-XX:MaxRAMPercentage=75.0` (heap dimensionado como % del límite de memoria del *contenedor*, no del host) y `-XX:+ExitOnOutOfMemoryError` (falla rápido y deja que Cloud Run reinicie una instancia limpia en vez de que la JVM siga corriendo degradada). Nota aparte, ya que Java 21 lo trae de fábrica: los **virtual threads** (JEP 444) están disponibles con `spring.threads.virtual.enabled=true` — es una palanca de configuración de la app, no del Dockerfile, pero vale mencionarla dado que estamos en Java 21.

---

### C) Centralización de secretos, variables de entorno y API keys en GCP

**Secret Manager** es el servicio nativo para esto, con integración directa a Cloud Run **sin sidecar**, de dos formas configurables al desplegar:

1. **Variable de entorno**: `--set-secrets=API_KEY=mi-secreto:latest` — el valor se resuelve una vez, al arrancar la instancia del contenedor, y queda fijo mientras esa instancia viva.
2. **Volumen montado**: `--set-secrets=/secrets/api-key=mi-secreto:latest` — se monta como archivo en un tmpfs en memoria, y **sí puede refrescarse a una versión nueva del secreto mientras la instancia sigue corriendo**, sin reiniciar el contenedor. Esta es la diferencia real entre ambos mecanismos y vale la pena elegir según si necesitas rotación en caliente.

En ambos casos, la cuenta de servicio de runtime del servicio Cloud Run necesita el rol `roles/secretmanager.secretAccessor` acotado al secreto puntual — no un rol a nivel de proyecto.

**Consumo desde Spring Boot** — dos opciones, y recomiendo la primera:

- **(a) Variable de entorno simple** (recomendado): Cloud Run inyecta `API_KEY` como variable de entorno real del proceso; `application.yaml` la referencia con el binding relajado normal de Spring: `some.api.key: ${API_KEY}`. Cero dependencias nuevas, cero llamadas de red extra al arrancar, comportamiento idéntico en local y en Cloud Run.
- **(b) `spring-cloud-gcp-starter-secretmanager`**: permite referenciar `sm://mi-secreto` directamente en la config (`some.api.key: ${sm://mi-secreto}`), con la app llamando a la API de Secret Manager al arrancar vía Application Default Credentials. Tiene sentido cuando los secretos deben resolverse de forma uniforme entre distintos tipos de cómputo (GKE + Compute Engine + local) sin inyección a nivel de plataforma como la que ya da Cloud Run, o cuando se necesita recarga en caliente del lado de la JVM.

Para este proyecto elijo (a), por el mismo argumento anti-acoplamiento que ya usamos en la Sección 4.2 con Redis/Lettuce: Cloud Run ya resuelve la "integración nativa" que pide el enunciado, así que sumar el starter de Spring Cloud GCP agregaría una dependencia, un BOM de Spring Cloud GCP que hay que mantener alineado con Boot 4.1.0 (riesgo real de desalineación dado que Spring Cloud GCP históricamente va un paso detrás de los saltos mayores de Boot), y una llamada de red adicional al arrancar — para algo que un despliegue de un solo servicio en Cloud Run no necesita. Si en algún momento se vuelve requisito rotar secretos sin reiniciar, primero probaría la variante de **volumen montado** de la opción (a) — sigue siendo cero-dependencias — antes de sumar el starter.

---

### D) Pipeline de CI/CD (GitHub Actions)

Diseño completo en [`deploy/github-actions-deploy.yml`](deploy/github-actions-deploy.yml). **Nota importante**: ese archivo vive en `deploy/` a propósito, no en `.github/workflows/` — GitHub Actions solo reconoce y ejecuta workflows exactamente en esa ruta. Para activarlo de verdad habría que moverlo/copiarlo ahí; lo dejo en `deploy/` deliberadamente como diseño/referencia, no como pipeline corriendo.

Disparado por `push` a `main`. Pasos, en orden:

1. `actions/checkout@v4`
2. `actions/setup-java@v4` con Temurin 21 — coincide exactamente con el toolchain de `build.gradle`.
3. **Compilar**: `./gradlew compileJava --no-daemon`
4. **Probar**: `./gradlew test --no-daemon`
5. **Empaquetar**: `./gradlew bootJar -x test --no-daemon` (los tests ya corrieron en el paso 4, no se repiten)
6. **Autenticar contra GCP**: `google-github-actions/auth@v2` usando **Workload Identity Federation**, no una llave JSON de larga duración.
7. **Construir y publicar la imagen** con el `Dockerfile` de la parte B, tag inmutable `${{ github.sha }}`, push a Artifact Registry.
8. **Desplegar a Cloud Run**: `google-github-actions/deploy-cloudrun@v2`, apuntando a la imagen recién publicada — crea una revisión nueva y mueve el tráfico, dejando la revisión anterior disponible para rollback sin rebuild.

**Por qué Workload Identity Federation y no una llave JSON**: el token OIDC que GitHub emite para cada ejecución se intercambia por una credencial de GCP de corta duración (~1h), acotada por un pool/provider a este repositorio y a la rama `main` específicamente (condición de atributo tipo `assertion.repository == 'org/products-api' && assertion.ref == 'refs/heads/main'`). No hay ningún secreto de larga vida guardado en GitHub — nada que rotar, nada que se pueda filtrar por un log mal configurado o una dependencia comprometida. Es la postura actual recomendada por Google y GitHub; muchas organizaciones ya lo hacen obligatorio vía la política `iam.disableServiceAccountKeyCreation`, que directamente deshabilita la creación de llaves JSON.
