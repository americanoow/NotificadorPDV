package notificador.modelo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Classifica dias de atraso em faixas, ex.: 15, 30, 45, 60, 75, 90 -> "15 dias" ... "90+ dias". */
public final class FaixasAtraso {

    private final List<Integer> limites;

    public FaixasAtraso(List<Integer> limites) {
        if (limites.isEmpty()) {
            throw new IllegalArgumentException("Informe pelo menos uma faixa em cobranca.faixas");
        }
        List<Integer> ordenados = new ArrayList<>(limites);
        Collections.sort(ordenados);
        this.limites = Collections.unmodifiableList(ordenados);
    }

    /** @return o limite da faixa (ex.: 30 para 30..44 dias) ou -1 se ainda não chegou à primeira. */
    public int classificar(int dias) {
        int faixa = -1;
        for (int limite : limites) {
            if (dias >= limite) {
                faixa = limite;
            }
        }
        return faixa;
    }

    public String rotulo(int faixa) {
        return faixa == limites.get(limites.size() - 1) ? faixa + "+ dias" : faixa + " dias";
    }

    public List<String> rotulos() {
        List<String> r = new ArrayList<>();
        for (int limite : limites) {
            r.add(rotulo(limite));
        }
        return r;
    }

    public int primeira() {
        return limites.get(0);
    }

    public List<Integer> limites() {
        return limites;
    }
}
