# Roteiro de apresentação

## 1. Iniciar pelo NetBeans

Abra a pasta como projeto Maven, pressione **F6** e acesse `http://localhost:8081`. O mural já apresenta três notícias, cada uma identificada com o curso de origem. Não é preciso estar autenticado para lê-las.

Apresente o **Mural Estudantil IFC Rio do Sul** como um trabalho de BCC em Desenvolvimento Web II. Ciência da Computação, Matemática e Física representam os tenants (empresas do enunciado). As notícias usam exemplos de estudos de Java, resolução de problemas e experimentos. O conflito entre Computação e Matemática existe apenas na simulação da atividade. No banco, os códigos antigos `aurora`, `horizonte` e `verde` foram preservados, respectivamente, para manter os vínculos e o histórico.

## 2. Demonstrar usuário comum

1. Entre em **Ciência da Computação** como `carlos`, senha `Usuario@123`.
2. Confira o nome do curso ativo e o perfil **Usuário comum** na barra superior.
3. Em **Notícias**, crie uma publicação sobre um projeto da turma. Abra o mural em outra aba: ela aparece publicamente, identificada como Ciência da Computação.
4. Edite a publicação e uma conta. Também é possível criar e excluir esses registros.
5. Tente acessar `/usuarios` diretamente: o sistema responde **403**.
6. Saia, entre em **Matemática** como `luisinho`, senha `Usuario@123`, e confira que a gestão não mostra os registros de Computação. O mural público continua mostrando notícias dos dois cursos.

## 3. Demonstrar proteção contra alteração de ID

1. Autenticado como Carlos, abra a edição de uma notícia própria e observe seu ID na URL.
2. Substitua-o pelo ID de uma notícia de Matemática: o sistema responde **404**, sem abrir a edição.
3. O mesmo bloqueio existe na edição e exclusão de contas e notícias, inclusive com POST manual. Um campo `empresaId` forjado no cadastro também não altera a empresa dos dados.

O ID de uma notícia de outro curso pode ser consultado diretamente no banco, com a aplicação parada, usando `SELECT id, empresa_id, titulo FROM noticias;`. Os testes automatizados cobrem a adulteração tanto das URLs quanto dos formulários.

## 4. Demonstrar administrador e cadastro

1. Saia e entre em **Ciência da Computação** como `admin`, senha `Admin@123`.
2. O menu **Usuários** aparece. Cadastre uma pessoa com perfil comum e uma senha de pelo menos oito caracteres.
3. Faça login com essa pessoa em Computação: o acesso funciona.
4. Tente fazer login com a mesma pessoa em Matemática: o acesso é recusado porque ela não possui vínculo com aquele curso.

## 5. Demonstrar CWP

Use o `admin` original, cuja identidade possui vínculo com os três cursos.

1. Após o primeiro login em Computação, o painel registra Ciência da Computação em **Cursos já administrados**.
2. Matemática aparece como **Bloqueado por CWP**, devido ao conflito simulado entre os dois cursos.
3. Acesse **Física** pelo painel: a troca é permitida, pois inicialmente não há conflito. O curso ativo e os dados exibidos mudam.
4. Saia e tente entrar como `admin` em Matemática: o login continua bloqueado por CWP. Abrir outro navegador também não apaga o histórico.
5. Notícias de Matemática continuam disponíveis no mural público, conforme o requisito de publicidade.

Se o primeiro acesso do administrador for em Matemática, o resultado é invertido: Computação fica bloqueada. Não apague o histórico para tentar contornar a política; essa persistência faz parte da atividade. Os testes verificam os dois sentidos em bancos isolados.

## 6. Mostrar o banco e o código

- `empresas.conflitos`: códigos de empresas com conflito, separados por vírgulas.
- `vinculos`: relaciona a identidade às empresas e define o perfil em cada uma.
- `acessos_administrativos`: histórico persistido para CWP.
- `noticias.empresa_id` e `contas.empresa_id`: associação obrigatória ao tenant.
- `usuarios.senha_hash`: senhas armazenadas em BCrypt.
- `LaboratorioService`: validações de empresa, perfil e conflito antes de acessar os dados.
- `SecurityConfig`: proteção das rotas, autenticação, sessão e CSRF.

Pare a aplicação para conectar ao arquivo H2 pelo NetBeans. Os exemplos em `database/manutencao.sql` são executados manualmente, não pela aplicação.

## 7. Executar os testes

No terminal da pasta, execute `mvn test`. No NetBeans, também é possível usar **Testar projeto**. Os testes usam banco em memória e não apagam os dados usados na apresentação.
