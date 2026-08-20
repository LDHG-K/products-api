## Propongo la siguiente estructura de paquetes para el proyecto de la seccion 2:

```
com.empresa.orders
├── domain                                          ← NÚCLEO (zero frameworks)
│   ├── model
│   │   ├── Order.java              ( reglas de negocio)
│   │   └── OrderStatus.java (esto puede ser un enum)
│   ├── event
│   │   └── OrderConfirmedEvent.java (Estructura de datos para eventos de dominio)
│   └── exception
│       ├── OrderNotFoundException.java 
│       └── InvalidOrderStateException.java
│
├── application                                     CASOS DE USO
│   ├── port
│   │   ├── in                      (puertos de entrada)
│   │   │   ├── CreateOrderUseCase.java
│   │   │   ├── ConfirmOrderUseCase.java
│   │   │   └── GetOrderUseCase.java
│   │   └── out                     (puertos de salida) 
│   │       ├── OrderRepositoryPort.java
│   │       ├── OrderEventPublisherPort.java
│   │       └── OrderNotificationPort.java
│   └── service
│       ├── CreateOrderService.java   (implementa CreateOrderUseCase)
│       ├── ConfirmOrderService.java  (implementa ConfirmOrderUseCase)
│       └── GetOrderService.java      (implementa GetOrderUseCase)
│
└── infrastructure                                  ADAPTADORES
├── adapter
│   ├── in
│   │   └── web
│   │       ├── OrderController.java        (adaptador PRIMARIO — REST)
│   │       ├── dto/  (CreateOrderRequest, OrderResponse)
│   │       └── mapper/OrderWebMapper.java
│   └── out
│       ├── persistence     (adaptador SECUNDARIO — PostgreSQL/JPA)
│       │   ├── OrderPersistenceAdapter.java
│       │   ├── entity/  (OrderJpaEntity, OrderItemEmbeddable)
│       │   ├── mapper/OrderPersistenceMapper.java
│       │   └── springdata/SpringDataOrderRepository.java
│       ├── messaging        (adaptador SECUNDARIO — GCP Pub/Sub)
│       │   └── PubSubOrderEventPublisherAdapter.java
│       └── notification     (adaptador SECUNDARIO — Email)
│           └── EmailOrderNotificationAdapter.java
└── config
└── OrdersBeanConfiguration.java  (configuración de beans y dependencias)
```