package notificador.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Lê o ficheiro config.properties e expõe as opções com valores padrão.
 * Para acrescentar uma opção nova (ex.: token do WhatsApp, chave da IA),
 * basta criar um getter aqui e a linha correspondente no .properties.
 */
public final class AppConfig {

    private static final Logger LOG = Logger.getLogger(AppConfig.class.getName());

    private final Properties props = new Properties();

    private AppConfig() {
    }

    /** Carrega a configuração; se o ficheiro não existir, usa só os padrões. */
    public static AppConfig carregar(File arquivo) {
        AppConfig cfg = new AppConfig();
        if (arquivo.isFile()) {
            try (InputStream in = new FileInputStream(arquivo);
                 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                cfg.props.load(reader);
                LOG.info("Configuração carregada de " + arquivo.getAbsolutePath());
            } catch (IOException e) {
                LOG.warning("Falha ao ler " + arquivo + ": " + e.getMessage() + " - usando padrões.");
            }
        } else {
            LOG.warning("Ficheiro " + arquivo.getAbsolutePath() + " não encontrado - usando padrões.");
        }
        return cfg;
    }

    // --- Base de dados ---------------------------------------------------

    public String dbHost()    { return texto("db.host", "127.0.0.1"); }
    public int    dbPorta()   { return inteiro("db.porta", 3306); }
    public String dbBase()    { return texto("db.base", "tagcomercio"); }
    public String dbUsuario() { return texto("db.usuario", "root"); }
    public String dbSenha()   { return texto("db.senha", ""); }

    // --- Agendamento -----------------------------------------------------

    public int intervaloMinutos()      { return inteiro("agendador.intervalo_minutos", 60); }
    public int atrasoInicialSegundos() { return inteiro("agendador.atraso_inicial_segundos", 10); }

    // --- Verificações ----------------------------------------------------

    public boolean estoqueAtivo()            { return booleano("estoque.ativo", true); }
    public boolean estoqueIgnorarMinimoZero() { return booleano("estoque.ignorar_minimo_zero", false); }
    public int     estoqueIgnorarSemVendaDias() { return inteiro("estoque.ignorar_sem_venda_dias", 0); }

    public boolean       cobrancaAtivo()             { return booleano("cobranca.ativo", true); }
    public List<Integer> cobrancaFaixas()            { return listaInteiros("cobranca.faixas", "15,30,45,60,75,90"); }
    public List<Integer> cobrancaModosIgnorados()    { return listaInteiros("cobranca.modos_ignorados", "6,7"); }
    public List<Integer> cobrancaClientesIgnorados() { return listaInteiros("cobranca.clientes_ignorados", "80"); }

    public boolean comprasAtivo()               { return booleano("compras.ativo", true); }
    public int     comprasCoberturaAlvoDias()    { return inteiro("compras.cobertura_alvo_dias", 30); }
    public int     comprasCoberturaMinimaDias()  { return inteiro("compras.cobertura_minima_dias", 10); }
    public int     comprasCoberturaExcessoDias() { return inteiro("compras.cobertura_excesso_dias", 120); }
    public int     comprasTendenciaPct()         { return inteiro("compras.tendencia_pct", 30); }
    public int     comprasMinVendas90Dias()      { return inteiro("compras.min_vendas_90_dias", 3); }
    public int     comprasValorMinimoParado()    { return inteiro("compras.valor_minimo_parado", 50); }
    public int     comprasMaisVendidosPct()      { return inteiro("compras.mais_vendidos_pct", 80); }
    public int     comprasMaisVendidosMinVendas() { return inteiro("compras.mais_vendidos_min_vendas", 12); }
    public int     comprasAvisoAntesMinimoDias() { return inteiro("compras.aviso_antes_minimo_dias", 7); }
    public int     comprasSazonalMinAnos()       { return inteiro("compras.sazonal_min_anos", 3); }
    public int     comprasSazonalMinVendas()     { return inteiro("compras.sazonal_min_vendas", 10); }

    public double comprasSazonalFator() {
        String v = texto("compras.sazonal_fator", "1.4").replace(',', '.');
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            LOG.warning("Valor inválido para compras.sazonal_fator - usando 1.4");
            return 1.4;
        }
    }

    /** Data fixa para a análise de compras (simular outra época); null = automático. */
    public Date comprasDataReferencia() {
        String v = texto("compras.data_referencia", "");
        if (v.isEmpty()) {
            return null;
        }
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy");
            fmt.setLenient(false);
            return fmt.parse(v);
        } catch (ParseException e) {
            LOG.warning("compras.data_referencia inválida (use dd/mm/aaaa): " + v + " - usando automático");
            return null;
        }
    }

    // --- Histórico -------------------------------------------------------

    public File historicoArquivo()   { return new File(texto("historico.arquivo", "historico_notificacoes.tsv")); }
    public int  historicoRetencaoDias() { return inteiro("historico.dias_retencao", 180); }

    // --- Ecrã ------------------------------------------------------------

    public int maxLinhasBalao() { return inteiro("notificacao.max_linhas_balao", 5); }

    // --- Integrações futuras ---------------------------------------------

    public boolean whatsappAtivo() { return booleano("whatsapp.ativo", false); }
    public boolean iaAtivo()       { return booleano("ia.ativo", false); }

    /** Acesso genérico, útil para módulos novos lerem as suas próprias chaves. */
    public String texto(String chave, String padrao) {
        return props.getProperty(chave, padrao).trim();
    }

    private List<Integer> listaInteiros(String chave, String padrao) {
        List<Integer> lista = new ArrayList<>();
        for (String parte : texto(chave, padrao).split(",")) {
            String p = parte.trim();
            if (p.isEmpty()) {
                continue;
            }
            try {
                lista.add(Integer.parseInt(p));
            } catch (NumberFormatException e) {
                LOG.warning("Valor inválido em " + chave + ": " + p + " - ignorado");
            }
        }
        return lista;
    }

    private int inteiro(String chave, int padrao) {
        try {
            return Integer.parseInt(texto(chave, String.valueOf(padrao)));
        } catch (NumberFormatException e) {
            LOG.warning("Valor inválido para " + chave + " - usando " + padrao);
            return padrao;
        }
    }

    private boolean booleano(String chave, boolean padrao) {
        return Boolean.parseBoolean(texto(chave, String.valueOf(padrao)));
    }
}
