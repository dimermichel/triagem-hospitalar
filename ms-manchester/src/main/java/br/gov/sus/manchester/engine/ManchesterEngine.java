package br.gov.sus.manchester.engine;

import br.gov.sus.manchester.dto.TriagemPayloadDTO;
import br.gov.sus.manchester.enums.NivelConsciencia;
import br.gov.sus.manchester.model.CorManchester;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Motor de classificação baseado no Protocolo de Manchester.
 *
 * Implementa os discriminadores clínicos mais críticos dos 50 fluxogramas.
 * A lógica segue uma árvore de decisão: sinais de ameaça imediata à vida
 * são checados primeiro (VERMELHO), depois os demais em ordem decrescente de urgência.
 *
 * Evolução futura: substituir/complementar com modelo ML treinado em dados SUS.
 */
@ApplicationScoped
public class ManchesterEngine {

    private static final Logger LOG = Logger.getLogger(ManchesterEngine.class);

    public CorManchester classificar(TriagemPayloadDTO p) {
        LOG.debugf("Classificando: queixa='%s', dor=%d, consciência=%s",
                p.getQueixaPrincipal(), p.getIntensidadeDor(), p.getNivelConsciencia());

        // ─── VERMELHO — Ameaça imediata à vida ───────────────────────────────
        if (isInconsciente(p))          return vermelho("Inconsciente");
        if (isParadaCardiorrespiratoria(p)) return vermelho("PCR suspeita");
        if (isSatBaixaCritica(p))       return vermelho("SatO2 < 85%");
        if (isPressaoCritica(p))        return vermelho("PA crítica");
        if (isDorPeitoBracoSudorese(p)) return vermelho("Dor no peito + irradiação/sudorese");
        if (isAVCSuspeito(p))           return vermelho("AVC suspeito");
        if (isSepseGrave(p))            return vermelho("Sepse grave");

        // ─── LARANJA — Muito urgente (10 min) ────────────────────────────────
        if (isConfusoOuSonolento(p))    return laranja("Confusão / sonolência");
        if (isDorPeitoSemAlerta(p))     return laranja("Dor no peito sem sinais críticos");
        if (isFaltaArGrave(p))          return laranja("Dispneia grave");
        if (isFreqCardiacaAlta(p))      return laranja("FC > 130 bpm");
        if (isSatBaixaModerada(p))      return laranja("SatO2 85-90%");
        if (isFebreAltaComRisco(p))     return laranja("Febre alta em grupo de risco");
        if (isDorIntensaSubitaAbdomen(p)) return laranja("Dor abdominal intensa e súbita");
        if (isGestanteComSinaisAlerta(p)) return laranja("Gestante com sinais de alerta");
        if (isIdadeCritica(p) && isDorModerada(p)) return laranja("Criança/idoso com dor moderada");

        // ─── AMARELO — Urgente (60 min) ──────────────────────────────────────
        if (isDorModeradaGeral(p))      return amarelo("Dor moderada");
        if (isFebreAlta(p))             return amarelo("Febre alta");
        if (isSintomaSubitoSemCritico(p)) return amarelo("Início súbito sem discriminador grave");
        if (isVomitosFrequentes(p))     return amarelo("Vômitos frequentes");

        // ─── VERDE — Pouco urgente (120 min) ─────────────────────────────────
        if (isDorLeve(p))               return verde("Dor leve");
        if (isFebreModerada(p))         return verde("Febre moderada");

        // ─── AZUL — Não urgente (240 min) ────────────────────────────────────
        return CorManchester.AZUL;
    }

    // =========================================================================
    // Discriminadores — VERMELHO
    // =========================================================================

    private boolean isInconsciente(TriagemPayloadDTO p) {
        return NivelConsciencia.INCONSCIENTE.equals(p.getNivelConsciencia());
    }

    private boolean isParadaCardiorrespiratoria(TriagemPayloadDTO p) {
        var sv = p.getSinaisVitais();
        if (sv == null) return false;
        return sv.getFrequenciaCardiaca() != null && sv.getFrequenciaCardiaca() < 30;
    }

    private boolean isSatBaixaCritica(TriagemPayloadDTO p) {
        var sv = p.getSinaisVitais();
        return sv != null && sv.getSaturacaoOxigenio() != null && sv.getSaturacaoOxigenio() < 85;
    }

    private boolean isPressaoCritica(TriagemPayloadDTO p) {
        var sv = p.getSinaisVitais();
        if (sv == null) return false;
        boolean hipotensao = sv.getPressaoSistolica() != null && sv.getPressaoSistolica() < 80;
        boolean hipertensao = sv.getPressaoSistolica() != null && sv.getPressaoSistolica() > 220;
        return hipotensao || hipertensao;
    }

    private boolean isDorPeitoBracoSudorese(TriagemPayloadDTO p) {
        String q = normalize(p.getQueixaPrincipal());
        return (q.contains("dor no peito") || q.contains("dor peito") || q.contains("dor toracica"))
                && (q.contains("braco") || q.contains("mandibula") || q.contains("suor") || q.contains("sudore"));
    }

    private boolean isAVCSuspeito(TriagemPayloadDTO p) {
        String q = normalize(p.getQueixaPrincipal());
        return q.contains("avc") || q.contains("derrame") ||
               (q.contains("fraqueza") && q.contains("face")) ||
               (q.contains("dificuldade") && q.contains("falar")) ||
               p.isInicioSubito() && (q.contains("confus") || q.contains("visão turva"));
    }

    private boolean isSepseGrave(TriagemPayloadDTO p) {
        var sv = p.getSinaisVitais();
        if (sv == null) return false;
        boolean febre = sv.getTemperatura() != null && sv.getTemperatura() > 38.5;
        boolean taqui = sv.getFrequenciaCardiaca() != null && sv.getFrequenciaCardiaca() > 120;
        boolean hipot = sv.getPressaoSistolica() != null && sv.getPressaoSistolica() < 90;
        return febre && taqui && hipot;
    }

    // =========================================================================
    // Discriminadores — LARANJA
    // =========================================================================

    private boolean isConfusoOuSonolento(TriagemPayloadDTO p) {
        return NivelConsciencia.CONFUSO.equals(p.getNivelConsciencia())
                || NivelConsciencia.SONOLENTO.equals(p.getNivelConsciencia());
    }

    private boolean isDorPeitoSemAlerta(TriagemPayloadDTO p) {
        String q = normalize(p.getQueixaPrincipal());
        return q.contains("dor no peito") || q.contains("dor torácica");
    }

    private boolean isFaltaArGrave(TriagemPayloadDTO p) {
        String q = normalize(p.getQueixaPrincipal());
        var sv = p.getSinaisVitais();
        boolean dispneia = q.contains("falta de ar") || q.contains("dispneia") || q.contains("dificuldade respirar");
        boolean frResp = sv != null && sv.getFrequenciaRespiratoria() != null && sv.getFrequenciaRespiratoria() > 30;
        return dispneia || frResp;
    }

    private boolean isFreqCardiacaAlta(TriagemPayloadDTO p) {
        var sv = p.getSinaisVitais();
        return sv != null && sv.getFrequenciaCardiaca() != null && sv.getFrequenciaCardiaca() > 130;
    }

    private boolean isSatBaixaModerada(TriagemPayloadDTO p) {
        var sv = p.getSinaisVitais();
        return sv != null && sv.getSaturacaoOxigenio() != null
                && sv.getSaturacaoOxigenio() >= 85 && sv.getSaturacaoOxigenio() <= 90;
    }

    private boolean isFebreAltaComRisco(TriagemPayloadDTO p) {
        var sv = p.getSinaisVitais();
        boolean febreAlta = sv != null && sv.getTemperatura() != null && sv.getTemperatura() > 39.5;
        return febreAlta && (p.getIdade() < 3 || p.getIdade() > 70 || p.isGestante()
                || temHistorico(p, "imunossupressão", "câncer", "diabetes"));
    }

    private boolean isDorIntensaSubitaAbdomen(TriagemPayloadDTO p) {
        String q = normalize(p.getQueixaPrincipal());
        return p.getIntensidadeDor() >= 8
                && (q.contains("abdome") || q.contains("abdômen") || q.contains("barriga"))
                && p.isInicioSubito();
    }

    private boolean isGestanteComSinaisAlerta(TriagemPayloadDTO p) {
        return p.isGestante() && (
                p.getIntensidadeDor() >= 7
                || isFaltaArGrave(p)
                || (p.getSinaisVitais() != null && p.getSinaisVitais().getPressaoSistolica() != null
                        && p.getSinaisVitais().getPressaoSistolica() > 160)
        );
    }

    private boolean isIdadeCritica(TriagemPayloadDTO p) {
        return p.getIdade() < 2 || p.getIdade() > 80;
    }

    private boolean isDorModerada(TriagemPayloadDTO p) {
        return p.getIntensidadeDor() >= 5 && p.getIntensidadeDor() <= 7;
    }

    // =========================================================================
    // Discriminadores — AMARELO / VERDE
    // =========================================================================

    private boolean isDorModeradaGeral(TriagemPayloadDTO p) {
        return p.getIntensidadeDor() >= 5;
    }

    private boolean isFebreAlta(TriagemPayloadDTO p) {
        var sv = p.getSinaisVitais();
        return sv != null && sv.getTemperatura() != null && sv.getTemperatura() > 39.0;
    }

    private boolean isSintomaSubitoSemCritico(TriagemPayloadDTO p) {
        return p.isInicioSubito() && p.getHorasSintoma() != null && p.getHorasSintoma() <= 6;
    }

    private boolean isVomitosFrequentes(TriagemPayloadDTO p) {
        String q = normalize(p.getQueixaPrincipal());
        return q.contains("vômito") || q.contains("vomito");
    }

    private boolean isDorLeve(TriagemPayloadDTO p) {
        return p.getIntensidadeDor() != null && p.getIntensidadeDor() >= 1 && p.getIntensidadeDor() <= 4;
    }

    private boolean isFebreModerada(TriagemPayloadDTO p) {
        var sv = p.getSinaisVitais();
        return sv != null && sv.getTemperatura() != null
                && sv.getTemperatura() > 37.8 && sv.getTemperatura() <= 39.0;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String normalize(String s) {
        if (s == null) return "";
        return java.text.Normalizer
                .normalize(s.toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "");
    }

    private boolean temHistorico(TriagemPayloadDTO p, String... termos) {
        if (p.getHistoricoMedico() == null) return false;
        for (String h : p.getHistoricoMedico()) {
            String hn = normalize(h);
            for (String t : termos) {
                if (hn.contains(t)) return true;
            }
        }
        return false;
    }

    private CorManchester vermelho(String motivo) {
        LOG.infof("Classificado como VERMELHO: %s", motivo);
        return CorManchester.VERMELHO;
    }

    private CorManchester laranja(String motivo) {
        LOG.infof("Classificado como LARANJA: %s", motivo);
        return CorManchester.LARANJA;
    }

    private CorManchester amarelo(String motivo) {
        LOG.debugf("Classificado como AMARELO: %s", motivo);
        return CorManchester.AMARELO;
    }

    private CorManchester verde(String motivo) {
        LOG.debugf("Classificado como VERDE: %s", motivo);
        return CorManchester.VERDE;
    }
}
