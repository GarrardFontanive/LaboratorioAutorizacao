# Laboratório de Autorização

Trabalho de Desenvolvimento Web II com tema do IFC Rio do Sul. O sistema tem um mural público de notícias e uma área de gestão separada para Ciência da Computação, Matemática e Física.

## Funcionalidades

- Login com curso, usuário e senha.
- Cadastro, edição e exclusão de notícias e contas do curso conectado.
- Cadastro de usuários restrito aos administradores do curso.
- Isolamento dos dados entre cursos (multi-tenant).
- Bloqueio de administração por conflito de interesses (CWP).
- Mural público com identificação do curso de cada notícia.
- Consulta de cursos e notícias públicas por Java RMI, com cliente Java independente.

## Tecnologias

Java 17+, Spring Boot, Spring Security, Thymeleaf, JDBC, H2 e Maven.

## Como executar

Com Java 17 ou superior e Maven instalados:

```bash
git clone https://github.com/GarrardFontanive/LaboratorioAutorizacao.git
cd LaboratorioAutorizacao
mvn spring-boot:run
```

Acesse [http://localhost:8081](http://localhost:8081). No NetBeans, abra a pasta como projeto Maven e execute com **F6**.

O banco e os exemplos são criados automaticamente na primeira execução. Os dados ficam salvos na pasta `data/`, que não é enviada ao GitHub.

## Cliente Java RMI

O servidor RMI inicia junto com o site. Para gerar o servidor e o cliente independente:

```bash
mvn package
```

Com o site em execução, use outro terminal, na mesma pasta:

```bash
java -jar target/laboratorio-autorizacao-1.0.0-cliente-rmi.jar
```

O cliente consulta cursos e notícias públicas em outra JVM, usando o mesmo banco do site.
Para consultar apenas Ciência da Computação:

```bash
java -jar target/laboratorio-autorizacao-1.0.0-cliente-rmi.jar 127.0.0.1 1099 aurora
```

Veja a arquitetura, as portas e o roteiro de demonstração em [RMI.md](RMI.md).

## Acessos de demonstração

| Curso | Usuário | Senha | Perfil |
| --- | --- | --- | --- |
| Ciência da Computação | `carlos` | `Usuario@123` | Comum |
| Física | `pedro` | `Usuario@123` | Comum |
| Matemática | `luisinho` | `Usuario@123` | Comum |
| Qualquer um dos três cursos | `admin` | `Admin@123` | Administrador |

O administrador possui vínculo com os três cursos. Para demonstrar a CWP, Computação e Matemática têm um conflito fictício: administrar um deles bloqueia o outro, mesmo após sair do sistema. Física permanece disponível. Essa regra é apenas uma simulação acadêmica.

## Testes

```bash
mvn test
```

Os testes usam um banco em memória, separado dos dados locais.

## Documentação

- [Roteiro de apresentação](ROTEIRO.md)
- [Guia de execução e autorização](GUIA.md)
- [Exemplos de manutenção no banco](database/manutencao.sql)

Os cursos representam as empresas do enunciado. Sua manutenção é feita diretamente no banco, na tabela `empresas`. Projeto acadêmico não oficial do IFC.
