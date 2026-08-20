# products-api
exam



# Pregunta: analisis de codigo.

Mi primera recomendación es que este nombramiento es mas adecuado para una interfaz, no para una clase que lleva una implementacion 
`public class OrderService {`

la primera violacion a la responsabilidad unica que tiene esta clase es que tiene multiples responsabilidades en su primera y unica funcion

el servicio, hace 4 cosas que no estan en su responsabilidad saber como resolver
1. Procesar el pago
2. Enviar un email de confirmacion
3. Actualizar el inventario
4. Conexiones a la base de datos

no existe un constructor para inyectar las dependencias de la clase, es decir los otros servicios.
las dependencias deben de ser los respectivos servicios que resuelven cada una de las responsabilidades de la clase como un servicio de que sabe como procesar el pago, enviar mail y actualizar el inventario.


1. Crea una conexion a la base de datos donde se expone el endpoint de la base de datos forzando dependencia a la actualizacion del codigo para cambiarla y expone las credenciales de acceso al repositorio. 
2. no se esta usando prepared statements para evitar la vulnercion a un ataque de inyeccion SQL
3. hay una fuga de recursos ya que no se esta cerrando la conexion a la base de datos. (el pool de conexiones esta sufriendo con esto)
4. no hay manejo de transacciones. no existe un @Transactional para manejar la atomicidad de las operaciones que se realizan en la base de datos, lo que puede llevar a inconsistencias si alguna operación falla.

Suifero este enfoque:

```
public class OrderService {
private final OrderRepository orderRepository;
private final PaymentService paymentService;
private final NotificationService notificationService;
private final InventoryService inventoryService;

    @Transactional
    public void processOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        PaymentResult payment = paymentService.charge(order);
        inventoryService.decrementStock(order);
        notificationService.sendConfirmation(order, payment);
    }
}
```

para optimizar un poco estas operaciones se puede usar virtual threads, para hacer el envio de email. (u otra notidicacion a sistemas satelites como un dashboard que es muy usado en estos contextos) 
Dejar las operaciones sincronas que serian la persistencia de la orden y la de actualizacion de inventario. esto necesita atomicidad mas que un paralelismo.

