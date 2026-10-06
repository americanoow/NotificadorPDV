package notificador.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.text.Format;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;

/**
 * Tabela só de leitura com um filtro por categoria, caixa de pesquisa e contador.
 * Colunas com números/datas ordenam pelo valor real (não pelo texto).
 */
final class PainelTabela extends JPanel {

    private static final String TODOS = "Todos";

    private final DefaultTableModel modelo;
    private final JTable tabela;
    private final TableRowSorter<DefaultTableModel> ordenador;
    private final JComboBox<String> filtro = new JComboBox<>();
    private final JTextField pesquisa = new JTextField(22);
    private final JLabel contador = new JLabel();
    private final int colunaFiltro;

    private int colunaCor = -1;
    private Map<String, Color> cores = new HashMap<>();

    /**
     * @param colunaFiltro índice da coluna cujo valor é comparado com a opção do filtro
     */
    PainelTabela(String[] colunas, int[] larguras, String rotuloFiltro, List<String> opcoesFiltro, int colunaFiltro) {
        super(new BorderLayout(0, 6));
        this.colunaFiltro = colunaFiltro;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        modelo = new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int linha, int coluna) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int coluna) {
                for (int i = 0; i < getRowCount(); i++) {
                    Object v = getValueAt(i, coluna);
                    if (v != null) {
                        return v.getClass();
                    }
                }
                return Object.class;
            }
        };
        tabela = new JTable(modelo) {
            @Override
            public Component prepareRenderer(TableCellRenderer r, int linha, int coluna) {
                Component c = super.prepareRenderer(r, linha, coluna);
                if (!isRowSelected(linha)) {
                    Color cor = null;
                    if (colunaCor >= 0) {
                        Object v = getModel().getValueAt(convertRowIndexToModel(linha), colunaCor);
                        cor = cores.get(String.valueOf(v));
                    }
                    c.setBackground(cor != null ? cor : getBackground());
                }
                return c;
            }
        };
        tabela.setRowHeight(tabela.getRowHeight() + 6);
        tabela.setShowVerticalLines(false);
        tabela.setGridColor(new Color(225, 225, 225));
        tabela.getTableHeader().setReorderingAllowed(false);
        ordenador = new TableRowSorter<>(modelo);
        tabela.setRowSorter(ordenador);
        tabela.setFillsViewportHeight(true);
        for (int i = 0; i < larguras.length; i++) {
            tabela.getColumnModel().getColumn(i).setPreferredWidth(larguras[i]);
        }

        filtro.addItem(TODOS);
        for (String opcao : opcoesFiltro) {
            filtro.addItem(opcao);
        }
        filtro.addActionListener(e -> aplicarFiltro());
        pesquisa.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { aplicarFiltro(); }
            @Override public void removeUpdate(DocumentEvent e)  { aplicarFiltro(); }
            @Override public void changedUpdate(DocumentEvent e) { aplicarFiltro(); }
        });

        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filtros.add(new JLabel(rotuloFiltro + ":"));
        filtros.add(filtro);
        filtros.add(new JLabel("   Pesquisar:"));
        filtros.add(pesquisa);
        filtros.add(contador);

        add(filtros, BorderLayout.NORTH);
        add(new JScrollPane(tabela), BorderLayout.CENTER);
    }

    /** Mostra os valores da coluna com o formato dado, alinhados à direita. null aparece como "—". */
    void formatarColuna(int coluna, Format formato) {
        tabela.getColumnModel().getColumn(coluna).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object valor) {
                setHorizontalAlignment(SwingConstants.RIGHT);
                setText(valor == null ? "—" : formato.format(valor));
            }
        });
    }

    /** Pinta a linha inteira conforme o valor (texto) de uma coluna. */
    void colorirPor(int coluna, Map<String, Color> coresPorValor) {
        this.colunaCor = coluna;
        this.cores = new HashMap<>(coresPorValor);
        tabela.repaint();
    }

    /** Chamado com o índice da linha no modelo (ordem de setLinhas), ou -1 sem seleção. */
    void aoSelecionar(IntConsumer acao) {
        tabela.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int v = tabela.getSelectedRow();
                acao.accept(v < 0 ? -1 : tabela.convertRowIndexToModel(v));
            }
        });
    }

    /** Substitui todo o conteúdo da tabela. */
    void setLinhas(List<Object[]> linhas) {
        modelo.setRowCount(0);
        for (Object[] l : linhas) {
            modelo.addRow(l);
        }
        aplicarFiltro();
        tabela.scrollRectToVisible(new Rectangle(0, 0, 1, 1));
    }

    /** Selecciona a opção no filtro (null = todos). */
    void filtrar(String opcao) {
        filtro.setSelectedItem(opcao == null ? TODOS : opcao);
    }

    private void aplicarFiltro() {
        List<RowFilter<Object, Object>> filtros = new ArrayList<>();
        Object opcao = filtro.getSelectedItem();
        if (opcao != null && !TODOS.equals(opcao)) {
            filtros.add(RowFilter.regexFilter("^" + Pattern.quote(opcao.toString()) + "$", colunaFiltro));
        }
        String texto = pesquisa.getText().trim();
        if (!texto.isEmpty()) {
            filtros.add(RowFilter.regexFilter("(?i)" + Pattern.quote(texto)));
        }
        ordenador.setRowFilter(filtros.isEmpty() ? null : RowFilter.andFilter(filtros));
        contador.setText("   " + ordenador.getViewRowCount() + " de " + modelo.getRowCount() + " registro(s)");
    }
}
