# Simulação de Microservices Resilientes (Java)

Projeto acadêmico para a disciplina **Padrões de Projeto**, no IFBA – Campus SAJ.  
Tema: **Microservices resilientes com aplicação de padrões arquiteturais**.

## 📂 Estrutura do Repositório

```
versao_inicial/
├── UsuarioService.java
├── PedidoService.java
└── PagamentoService.java

versao_refatorada/
├── ApiGatewayService.java
├── UsuarioService.java
├── PedidoService.java
├── PagamentoService.java
├── utils/
    ├── HttpClient.java
    ├── CircuitBreaker.java
    └── BulkheadExecutor.java

```

## ⚠️ Versão Inicial (com falhas)

- Serviços se chamam diretamente (`UsuarioService → PedidoService → PagamentoService`).
- Não há tratamento de falhas.
- Viola princípios SOLID (SRP, OCP, DIP).
- Exemplo de execução:
  ```bash
  javac *.java
  java UsuarioService   # porta 8000
  java PedidoService    # porta 8001
  java PagamentoService # porta 8002

- Testes:
    - http://localhost:8000/usuario/1
    - http://localhost:8000/usuario/1/pedidos
    - http://localhost:8001/pedido/101

## ✅ Versão Refatorada (com padrões)

- API Gateway centraliza chamadas (porta 9000).
- Circuit Breaker evita falhas em cascata.
- Bulkhead isola recursos por serviço.
- IoC usado no Gateway para injetar dependências.

```
javac *.java
# rodar em terminais separados
java UsuarioService       # porta 8000
java PedidoService        # porta 8001
java PagamentoService     # porta 8002
java ApiGatewayService    # porta 9000
```

## 📊 Comparativo

| Aspecto     | Inicial (falha)       | Refatorada (padrões)     |
| ----------- | --------------------- | ------------------------ |
| Comunicação | Direta entre serviços | API Gateway centralizado |
| Falhas      | Cascata               | Circuit Breaker          |
| Sobrecarga  | Global                | Bulkhead isolado         |
| SOLID       | Viola SRP, OCP, DIP   | DIP respeitado via IoC   |