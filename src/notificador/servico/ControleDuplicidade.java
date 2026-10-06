package notificador.servico;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Evita mostrar o mesmo alerta de hora a hora: cada chave só é notificada
 * uma vez por dia. À meia-noite a memória é limpa e os itens que continuem
 * em falta voltam a aparecer (lembrete diário).
 *
 * Fica só em memória: ao reiniciar o programa os alertas do dia reaparecem.
 * Se quiser persistir, grave o conjunto num ficheiro ou numa tabela própria.
 */
final class ControleDuplicidade {

    private LocalDate dia = LocalDate.now();
    private final Set<String> notificados = new HashSet<>();

    synchronized boolean jaNotificado(String chave) {
        renovarSeMudouODia();
        return notificados.contains(chave);
    }

    synchronized void registrar(String chave) {
        renovarSeMudouODia();
        notificados.add(chave);
    }

    private void renovarSeMudouODia() {
        LocalDate hoje = LocalDate.now();
        if (!hoje.equals(dia)) {
            dia = hoje;
            notificados.clear();
        }
    }
}
