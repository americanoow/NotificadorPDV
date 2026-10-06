package notificador.servico;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import notificador.modelo.ItemAlerta;
import notificador.modelo.TipoNotificacao;

/**
 * Resultado COMPLETO da última verificação de cada tipo. Ao contrário do
 * pop-up (que só mostra o que é novo no dia), aqui está tudo o que está
 * em alerta neste momento.
 */
public final class SituacaoAtual {

    private final Map<TipoNotificacao, List<ItemAlerta>> itens = new EnumMap<>(TipoNotificacao.class);
    private Date ultimaVerificacao;

    synchronized void atualizar(TipoNotificacao tipo, List<? extends ItemAlerta> encontrados) {
        itens.put(tipo, new ArrayList<ItemAlerta>(encontrados));
    }

    synchronized void marcarVerificacao(Date quando) {
        ultimaVerificacao = quando;
    }

    /** Cópia segura para a interface gráfica. */
    public synchronized Map<TipoNotificacao, List<ItemAlerta>> getItens() {
        Map<TipoNotificacao, List<ItemAlerta>> copia = new EnumMap<>(TipoNotificacao.class);
        for (Map.Entry<TipoNotificacao, List<ItemAlerta>> e : itens.entrySet()) {
            copia.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return copia;
    }

    /** null enquanto nenhuma verificação tiver terminado com sucesso. */
    public synchronized Date getUltimaVerificacao() {
        return ultimaVerificacao;
    }
}
