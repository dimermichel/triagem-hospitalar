package br.gov.sus.manchester.model;

/**
 * Cores do Protocolo de Manchester com prioridade e tempo máximo de espera.
 * prioridadeOrdem: 1 = mais urgente (facilita ordenação lexicográfica no DynamoDB SK).
 */
public enum CorManchester {

    VERMELHO("Emergência",       0,  1),
    LARANJA ("Muito urgente",   10,  2),
    AMARELO ("Urgente",         60,  3),
    VERDE   ("Pouco urgente",  120,  4),
    AZUL    ("Não urgente",    240,  5);

    private final String descricao;
    private final int tempoMaxEsperaMin;
    private final int prioridadeOrdem;

    CorManchester(String descricao, int tempoMaxEsperaMin, int prioridadeOrdem) {
        this.descricao = descricao;
        this.tempoMaxEsperaMin = tempoMaxEsperaMin;
        this.prioridadeOrdem = prioridadeOrdem;
    }

    public String getDescricao()         { return descricao; }
    public int getTempoMaxEsperaMin()    { return tempoMaxEsperaMin; }
    public int getPrioridadeOrdem()      { return prioridadeOrdem; }

    /** Emoji para exibição no painel */
    public String getEmoji() {
        return switch (this) {
            case VERMELHO -> "🔴";
            case LARANJA  -> "🟠";
            case AMARELO  -> "🟡";
            case VERDE    -> "🟢";
            case AZUL     -> "🔵";
        };
    }
}
