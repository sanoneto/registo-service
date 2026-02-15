# 🏋️‍♂️ Registo de Horas Service

Microserviço de backend desenvolvido em **Java 21** e **Spring Boot 3.5**, focado na gestão de assiduidade, planos de pagamento, autenticação centralizada e integrações avançadas com serviços externos.

## 🛠️ Stack Tecnológica

### Core
* **Java 21** (LTS) & Spring Boot 3.5.7
* **PostgreSQL** com **Flyway** (Migrations)
* **Redis** (Cache de alta velocidade)
* **Spring Security** (JWT Authentication)

### Integrações e IA
* **Spring AI (OpenAI)**: Processamento de linguagem natural e automação inteligente.
* **Google Calendar API**: Sincronização automática de treinos.
* **AWS S3**: Armazenamento de assinaturas digitais e documentos.
* **Telegram Bot SDK**: Interface de comunicação via chat.
* **RabbitMQ (AMQP)**: Mensageria assíncrona.

### Utilitários
* **MapStruct**: Mapeamento de objetos (DTO <-> Entity).
* **Lombok**: Produtividade e redução de boilerplate.
* **SpringDoc OpenAPI**: Documentação interativa (Swagger).

---

## 🏗️ Configuração do Ambiente

### Pré-requisitos
* JDK 21 instalada.
* Maven 3.x.
* PostgreSQL rodando.
* Redis rodando.

### Variáveis de Ambiente (`application.properties` ou `.env`)

```properties
# Base de Dados
spring.datasource.url=jdbc:postgresql://localhost:5432/registo_horas
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASS}

# Redis & Cache
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Google API (Credenciais de Serviço)
google.calendar.credentials.path=${PATH_TO_JSON_KEY}
google.calendar.id=primary

# Cloud Services (AWS S3)
spring.cloud.aws.s3.region=eu-west-1
spring.cloud.aws.credentials.access-key=${AWS_ACCESS_KEY}
spring.cloud.aws.credentials.secret-key=${AWS_SECRET_KEY}

# AI (OpenAI)
spring.ai.openai.api-key=${OPENAI_API_KEY}

Como ExecutarClonar o repositório:Bashgit clone [url-do-repositorio]
Compilar e executar:Bash mvn clean install
mvn spring-boot:run
Documentação:Aceda a http://localhost:8080/swagger-ui.html para visualizar e testar os endpoints.
🌐 Endpoints Principais
MétodoRotaDescrição
GET/api/precos-ptLista todos os packs de treino
POST/api/registros-treino/save-complexCria plano OU regista treinos com assinatura
GET/api/v1/eventosLista eventos agendados (Google Calendar)
POST/api/v1/eventosCria evento com X-Google-Token🛡️ 


Tratamento de Erros PadronizadoO projeto utiliza um GlobalExceptionHandler que 
retorna o record ErrorResponse, garantindo que o frontend receba erros no formato:JSON{
  "message": "Descrição amigável do erro",
  "status": 400
}