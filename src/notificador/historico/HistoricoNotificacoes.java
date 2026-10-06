package notificador.historico;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import notificador.modelo.ItemAlerta;
import notificador.modelo.Notificacao;
import notificador.modelo.RegistroHistorico;
import notificador.modelo.TipoNotificacao;

/**
 * Guarda todas as notificações emitidas num ficheiro de texto (TSV, UTF-8),
 * uma linha por item:  data/hora <TAB> tipo <TAB> chave <TAB> descrição
 *
 * O formato é simples de propósito: abre-se no Excel/Bloco de Notas e pode
 * ser trocado mais tarde por uma tabela própria no MySQL, se preferir.
 */
public final class HistoricoNotificacoes {

    private static final Logger LOG = Logger.getLogger(HistoricoNotificacoes.class.getName());
    private static final String FORMATO_DATA = "yyyy-MM-dd HH:mm:ss";

    private final File arquivo;
    private final List<RegistroHistorico> registros = new ArrayList<>();

    /** Lê o ficheiro e descarta registos mais antigos que {@code diasRetencao}. */
    public HistoricoNotificacoes(File arquivo, int diasRetencao) {
        this.arquivo = arquivo;
        carregar(diasRetencao);
    }

    /** Acrescenta todos os itens da notificação ao histórico (memória + ficheiro). */
    public synchronized void registrar(Notificacao n) throws IOException {
        List<RegistroHistorico> novos = new ArrayList<>();
        for (ItemAlerta item : n.getItens()) {
            novos.add(new RegistroHistorico(n.getDataHora(), n.getTipo(), item.chave(), item.descricao()));
        }
        gravar(novos, true);
        registros.addAll(novos);
    }

    /** Cópia de todos os registos, do mais recente para o mais antigo. */
    public synchronized List<RegistroHistorico> listar() {
        List<RegistroHistorico> copia = new ArrayList<>(registros);
        Collections.reverse(copia);
        return copia;
    }

    private void carregar(int diasRetencao) {
        if (!arquivo.isFile()) {
            return;
        }
        Calendar limite = Calendar.getInstance();
        limite.add(Calendar.DAY_OF_MONTH, -diasRetencao);
        SimpleDateFormat fmt = new SimpleDateFormat(FORMATO_DATA);
        int descartados = 0;

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(new FileInputStream(arquivo), StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = in.readLine()) != null) {
                String[] c = linha.split("\t", 4);
                if (c.length < 4) {
                    continue;
                }
                try {
                    Date data = fmt.parse(c[0]);
                    if (data.before(limite.getTime())) {
                        descartados++;
                        continue;
                    }
                    registros.add(new RegistroHistorico(data, TipoNotificacao.valueOf(c[1]), c[2], c[3]));
                } catch (ParseException | IllegalArgumentException e) {
                    descartados++; // linha corrompida ou tipo que deixou de existir
                }
            }
        } catch (IOException e) {
            LOG.warning("Falha ao ler o histórico " + arquivo + ": " + e.getMessage());
            return;
        }

        if (descartados > 0) {
            try {
                gravar(registros, false); // reescreve só com os registos válidos
            } catch (IOException e) {
                LOG.warning("Falha ao limpar o histórico: " + e.getMessage());
            }
        }
        LOG.info("Histórico: " + registros.size() + " registo(s) carregado(s).");
    }

    private void gravar(List<RegistroHistorico> lista, boolean acrescentar) throws IOException {
        File pasta = arquivo.getAbsoluteFile().getParentFile();
        if (pasta != null && !pasta.isDirectory()) {
            pasta.mkdirs();
        }
        SimpleDateFormat fmt = new SimpleDateFormat(FORMATO_DATA);
        try (Writer out = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(arquivo, acrescentar), StandardCharsets.UTF_8))) {
            for (RegistroHistorico r : lista) {
                out.write(fmt.format(r.getDataHora()) + '\t' + r.getTipo().name() + '\t'
                        + limpar(r.getChave()) + '\t' + limpar(r.getDescricao()) + System.lineSeparator());
            }
        }
    }

    /** Tabs e quebras de linha estragariam o formato TSV. */
    private static String limpar(String s) {
        return s == null ? "" : s.replaceAll("[\\t\\r\\n]+", " ");
    }
}
