# NotificadorPDV

Um programinha que roda em segundo plano no computador da loja e fica de olho no banco de dados do **TagComercio**. De tempos em tempos ele confere se alguma coisa precisa de atenção (produto acabando, cliente devendo, hora de fazer compra) e avisa pela bandeja do sistema. Assim ninguém precisa abrir o sistema só para lembrar.

## O que ele avisa

- **Estoque baixo**: produtos cujo estoque chegou no mínimo cadastrado.
- **Cobrança**: clientes com parcelas vencidas e não pagas no contas a receber, agrupados por tempo de atraso (15, 30, 45, 60, 75, 90 dias ou mais).
- **Sugestões de compra**: olha as vendas dos últimos meses e sugere o que aumentar (vende mais do que o estoque aguenta), o que reduzir (estoque parado demais) e o que comprar antes de uma época de alta, com base no que foi vendido nos mesmos meses dos anos anteriores.

Cada alerta é mostrado uma vez por dia. Se o problema continuar no dia seguinte, ele volta a aparecer como lembrete.

## Como ele avisa

- **Balão na bandeja** do sistema, com as primeiras linhas do alerta.
- **Central de Notificações**: uma janela com tudo o que está em alerta agora e o histórico de avisos. Abre clicando no balão ou com duplo clique no ícone.
- **Registro**: tudo fica no log e no arquivo `historico_notificacoes.tsv`.
- **Menu do ícone**: "Verificar agora" roda uma verificação na hora, e "Sair" encerra o programa.

## Requisitos

- Java 8 ou superior (JDK para compilar, JRE basta para rodar).
- MySQL com a base do TagComercio acessível pela rede local.
- O driver `mysql-connector-java-5.1.49.jar` já vem na pasta `lib/`.

## Configuração

Toda a configuração fica no `config.properties`, que deve ficar ao lado do `.jar`. Os principais pontos:

| Chave | O que faz |
|---|---|
| `db.host`, `db.porta`, `db.base`, `db.usuario`, `db.senha` | Conexão com o MySQL |
| `agendador.intervalo_minutos` | De quanto em quanto tempo verifica (padrão: 60) |
| `estoque.ativo`, `cobranca.ativo`, `compras.ativo` | Liga ou desliga cada tipo de alerta |
| `cobranca.faixas` | Faixas de atraso em dias |
| `cobranca.clientes_ignorados` | Clientes que não devem ser cobrados (ex.: o "BALCÃO") |
| `historico.dias_retencao` | Por quantos dias o histórico é guardado (padrão: 180) |

O arquivo tem comentários explicando cada opção. Como ele guarda a senha do banco em texto puro, cuide para que não fique em lugares abertos.

## Como rodar

**Windows**

1. Para compilar de novo (opcional, o `NotificadorPDV.jar` já está pronto): execute `compilar.bat`.
2. Para iniciar: execute `executar.bat`. Ele roda sem janela de console.
3. Para iniciar junto com o Windows, crie um atalho para o `executar.bat` dentro de `shell:startup`.

**Linux**

```sh
./compilar.sh
nohup java -jar NotificadorPDV.jar config.properties >/dev/null 2>&1 &
```

Para usar outro arquivo de configuração, passe o caminho como argumento: `java -jar NotificadorPDV.jar caminho/para/outro.properties`.

## Estrutura do projeto

```
src/notificador/
├── App.java            ponto de entrada: monta as peças e liga tudo
├── agendador/          executa as verificações no intervalo configurado
├── verificacao/        as consultas SQL (estoque baixo, cobrança)
├── ia/                 análise de sugestões de compra
├── servico/            orquestra um ciclo e controla a duplicidade dos avisos
├── canal/              por onde o aviso sai: bandeja, histórico, log, WhatsApp
├── ui/                 Central de Notificações
├── modelo/             classes de dados (produto, cliente, notificação...)
├── historico/          leitura e gravação do histórico em arquivo
├── config/             leitura do config.properties
└── db/                 conexão com o MySQL
```

### Como adicionar um novo alerta

1. Crie uma classe que implemente `Verificador` (em `verificacao/`), com a consulta SQL e o título da notificação.
2. Registre a classe no método `criarVerificadores` do `App.java`.
3. Se ele precisar de configuração própria, crie um getter no `AppConfig` e a linha correspondente no `config.properties`.

## Em andamento

- **WhatsApp**: o canal existe só como esqueleto (`WhatsAppCanal`) e ainda não envia mensagens. A opção `whatsapp.ativo` já está prevista.
- **IA**: a opção `ia.ativo` existe, mas ainda não há nenhum processador de IA registrado.

## Observações

- O aviso de duplicidade fica só na memória. Se o programa for reiniciado, os alertas do dia aparecem de novo.
- O programa só lê o banco de dados; ele não altera nada no TagComercio.
