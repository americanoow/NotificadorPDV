package notificador.modelo;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uma notificação pronta a ser entregue pelos canais (tray, WhatsApp...).
 * Agrupa todos os itens de um mesmo tipo encontrados num ciclo.
 */
public final class Notificacao {

    private final TipoNotificacao tipo;
    private final String titulo;
    private final List<? extends ItemAlerta> itens;
    private final Date dataHora = new Date();

    /**
     * Espaço livre para os processadores (ex.: IA) anexarem texto gerado,
     * como "sugestão de compra" ou "mensagem de cobrança". Os canais podem
     * ler estas entradas e incluí-las na mensagem.
     */
    private final Map<String, String> extras = new LinkedHashMap<>();

    public Notificacao(TipoNotificacao tipo, String titulo, List<? extends ItemAlerta> itens) {
        this.tipo = tipo;
        this.titulo = titulo;
        this.itens = Collections.unmodifiableList(itens);
    }

    /** Texto curto: as primeiras {@code maxLinhas} linhas + "... e mais N". */
    public String resumo(int maxLinhas) {
        StringBuilder sb = new StringBuilder();
        int limite = Math.min(maxLinhas, itens.size());
        for (int i = 0; i < limite; i++) {
            sb.append("• ").append(itens.get(i).descricao()).append('\n');
        }
        if (itens.size() > limite) {
            sb.append("... e mais ").append(itens.size() - limite).append(". Clique para ver todos.");
        }
        return sb.toString().trim();
    }

    /** Texto completo, uma linha por item, seguido dos extras. */
    public String textoCompleto() {
        StringBuilder sb = new StringBuilder();
        for (ItemAlerta item : itens) {
            sb.append(item.descricao()).append('\n');
        }
        for (Map.Entry<String, String> e : extras.entrySet()) {
            sb.append('\n').append(e.getKey()).append(":\n").append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    public TipoNotificacao getTipo()             { return tipo; }
    public String getTitulo()                     { return titulo; }
    public List<? extends ItemAlerta> getItens()  { return itens; }
    public Date getDataHora()                     { return dataHora; }
    public Map<String, String> getExtras()        { return extras; }
}
