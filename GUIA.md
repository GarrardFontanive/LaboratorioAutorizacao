# Guia do Laboratório de Autorização

Sistema web Java para a atividade de Desenvolvimento Web II, pronto para abrir como projeto Maven no Apache NetBeans.

A interface foi tematizada como um trabalho de estudante de Bacharelado em Ciência da Computação do IFC Rio do Sul: mural de projetos, grupos de estudo e extensão dos cursos de **Ciência da Computação, Matemática e Física**, com cores verde e vermelha e uma marca própria em formato de caderno. É um projeto acadêmico não oficial. As publicações e os conflitos são fictícios, não anúncios ou regras institucionais reais. Referência de contexto: [IFC Campus Rio do Sul](https://riodosul.ifc.edu.br/).

Os três cursos representam os tenants da atividade e aparecem no campo **Curso** do login. A estrutura `empresas` no banco e o parâmetro `empresa` dos formulários foram mantidos para atender ao modelo original. Para preservar conflitos e histórico existentes, os códigos internos continuam sendo `aurora` (Ciência da Computação), `horizonte` (Matemática) e `verde` (Física). A seleção mostra os nomes dos cursos; as senhas e as regras de autorização continuam iguais.

Se o banco já contiver os exemplos das versões anteriores, a inicialização adapta seus nomes e os textos originais para os cursos. Não exclui registros, não redefine senhas, não altera vínculos ou conflitos e não apaga o histórico CWP. Conteúdos personalizados são preservados.

## Abrir e executar no NetBeans

1. Tenha um JDK 17 ou superior e Maven instalado. O projeto usa Spring Boot 4.1.2, compatível com o Java 27 instalado nesta máquina.
2. No NetBeans, selecione **Arquivo → Abrir Projeto** e escolha esta pasta, que contém o `pom.xml`.
3. Aguarde o Maven baixar as dependências. Se necessário, configure o JDK em **Ferramentas → Plataformas Java**.
4. Clique com o botão direito no projeto e escolha **Executar** ou pressione **F6**. O `nbactions.xml` configura a execução de `spring-boot:run`.
5. Abra **http://localhost:8081** no navegador.

O servidor Tomcat já vem incorporado à aplicação. A execução usa o Maven; não é necessário cadastrar um servidor externo no NetBeans. A porta padrão do projeto é 8081 para evitar conflito com o Tomcat externo na 8080. Na primeira execução, as tabelas e os dados de demonstração são criados automaticamente. O banco fica em `data/laboratorio.mv.db`, relativo à pasta do projeto, e persiste após reiniciar.

Pelo terminal, dentro desta pasta:

```bash
mvn spring-boot:run
```

Para gerar e executar o arquivo distribuível:

```bash
mvn clean package
java -jar target/laboratorio-autorizacao-1.0.0.jar
```

Se a porta 8081 estiver ocupada:

```bash
PORT=8082 mvn spring-boot:run
```

## Usuários de demonstração

| Curso no login | Usuário | Senha | Perfil |
| --- | --- | --- | --- |
| Ciência da Computação | `admin` | `Admin@123` | Administrador |
| Matemática | `admin` | `Admin@123` | Administrador |
| Física | `admin` | `Admin@123` | Administrador |
| Ciência da Computação | `carlos` | `Usuario@123` | Comum |
| Matemática | `luisinho` | `Usuario@123` | Comum |
| Física | `pedro` | `Usuario@123` | Comum |

O usuário `admin` é **a mesma identidade** vinculada aos três cursos. Ciência da Computação e Matemática possuem um conflito **simulado para demonstrar a CWP**: o primeiro login administrativo em um deles impede administrar o outro. Física não tem conflito inicial e permanece disponível. Isso é apenas uma regra didática da atividade, não um conflito real entre os cursos. As credenciais acima são destinadas à demonstração acadêmica.

## Requisitos implementados

| Requisito da atividade | Implementação |
| --- | --- |
| Login com empresa, usuário e senha | Na temática, a empresa é apresentada como curso. Os três campos são obrigatórios e a identidade precisa ter vínculo com o curso escolhido. |
| Usuário altera somente os dados da própria empresa | O tenant vem da sessão autenticada. Consultas, alterações e exclusões usam também `empresa_id`, inclusive quando um ID é alterado manualmente na URL ou formulário. |
| Cadastro de notícias e contas | Ambos os perfis podem cadastrar, listar, editar e excluir os registros da empresa ativa. “Contas” foi interpretado como contas financeiras a pagar/receber. |
| Usuário comum não acessa cadastro de usuários | Menu oculto, rotas protegidas pelo Spring Security e verificação de perfil no serviço. |
| Administrador cadastra usuários apenas nas empresas administradas | O cadastro cria vínculo exclusivamente com a empresa ativa do administrador. Não aceita empresa informada pelo navegador. |
| Administrador não gerencia empresas conflitantes | CWP verifica o histórico administrativo persistido da mesma identidade, no login, na troca de empresa e nas operações privadas. |
| Empresas mantidas diretamente no banco | Não há tela de cadastro de empresas. A coluna `empresas.conflitos` contém os códigos das empresas conflitantes, separados por vírgula. |
| Notícias públicas com identificação da empresa | A rota `/` é pública e apresenta nome da empresa, autor, data, título e conteúdo. |

## Como funciona a CWP

CWP significa **Chinese Wall Policy**, ou política da muralha chinesa. A aplicação grava o primeiro acesso administrativo em `acessos_administrativos`. Os registros são compartilhados por todas as sessões da identidade e não são apagados pelo logout.

Antes de permitir o acesso, o sistema compara a empresa desejada com todas as já administradas. A declaração de conflito em qualquer um dos lados é suficiente para bloquear. O próprio acesso ao ambiente administrativo, incluindo o login, já registra a escolha; não é necessário publicar uma notícia para ativar a restrição.

Um bloqueio de banco por usuário serializa acessos concorrentes para evitar dois logins simultâneos em empresas conflitantes. As permissões e os conflitos são consultados novamente nas operações: uma mudança direta no banco também afeta sessões existentes. O histórico continua valendo mesmo se um vínculo antigo for removido.

O perfil pertence ao vínculo usuário/empresa. Como exigido no enunciado, a restrição CWP se aplica à administração. Notícias públicas permanecem acessíveis e um vínculo de usuário comum não concede administração. A identidade tem login único no sistema para correlacionar seu histórico entre empresas.

## Manutenção do banco

O H2 é um banco SQL local, sem instalação separada. O esquema está em `src/main/resources/schema.sql`. Exemplos de manutenção de empresas, conflitos e vínculos estão em [`database/manutencao.sql`](database/manutencao.sql).

Para conectar pelo NetBeans:

1. Execute a aplicação uma vez para criar o banco e depois pare a execução.
2. Em **Serviços → Bancos de Dados**, cadastre uma conexão H2. Se o driver não aparecer, adicione o JAR baixado pelo Maven: `~/.m2/repository/com/h2database/h2/2.4.240/h2-2.4.240.jar`. A classe do driver é `org.h2.Driver`.
3. Use a URL JDBC abaixo, usuário `sa` e senha vazia, salvo se você tiver configurado `DB_USER`/`DB_PASSWORD`.
4. Execute as instruções desejadas e **desconecte a conexão do NetBeans antes de iniciar novamente o sistema**. O banco em arquivo usa acesso exclusivo por processo.

```text
jdbc:h2:file:/caminho/do/projeto/data/laboratorio
```

Também é possível usar o cliente SQL no terminal, com a aplicação parada:

```bash
java -cp "$HOME/.m2/repository/com/h2database/h2/2.4.240/h2-2.4.240.jar" org.h2.tools.Shell \
  -url 'jdbc:h2:file:./data/laboratorio' -user sa
```

O código da empresa é usado na coluna de conflitos; mantenha-o em letras minúsculas e atualize as referências se precisar renomeá-lo. Se houver mais de uma empresa conflitante, informe, por exemplo, `aurora,horizonte`. O sistema interpreta esse campo nos dois sentidos, ainda que apenas um registro declare o conflito.

O console web do H2 está desativado. `DB_URL`, `DB_USER`, `DB_PASSWORD` e `PORT` podem ser definidos como variáveis de ambiente. Os dados iniciais só são inseridos quando a tabela de empresas está vazia; a adaptação de tema reconhece apenas os exemplos originais. Reiniciar a aplicação não restaura dados alterados nem limpa o histórico.

## Testes e apresentação

```bash
mvn test
```

Os testes de integração usam servidor HTTP real e H2 em memória, separado do banco da demonstração. Verificam login, renderização das telas, CRUD, isolamento entre tenants, cadastro por perfil, CWP após logout e em acessos concorrentes, revogação de acesso, CSRF e escape de HTML nas notícias.

Consulte [`ROTEIRO.md`](ROTEIRO.md) para uma sequência de demonstração dos requisitos.

## Organização

```text
pom.xml                         Dependências e build Maven
nbactions.xml                   Executar/depurar no NetBeans
database/manutencao.sql          Exemplos de manutenção direta no banco
src/main/java/.../security/      Login e autorização por perfil
src/main/java/.../service/       Regras de tenant, CWP e persistência JDBC
src/main/java/.../web/           Rotas, formulários e tratamento de erros
src/main/resources/schema.sql   Estrutura do banco
src/main/resources/templates/   Telas Thymeleaf em português
src/main/resources/static/css/  Estilos responsivos, sem CDN
src/main/resources/static/images/ Marca própria do projeto estudantil
src/test/java/                  Testes de integração
```

As senhas usam BCrypt; os formulários usam proteção CSRF; o conteúdo das notícias é renderizado com escape de HTML; o login usa proteção de sessão do Spring Security. A execução é local para o laboratório. Referências: [Spring Boot — requisitos](https://docs.spring.io/spring-boot/system-requirements.html) e [Spring Security — formulário de login](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html).
