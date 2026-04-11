package br.gov.sus.manchester.engine;

import br.gov.sus.manchester.dto.SinaisVitaisDTO;
import br.gov.sus.manchester.dto.TriagemPayloadDTO;
import br.gov.sus.manchester.enums.NivelConsciencia;
import br.gov.sus.manchester.model.CorManchester;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class ManchesterEngineTest {

    @Inject
    ManchesterEngine engine;

    @Test
    @DisplayName("Paciente inconsciente → VERMELHO")
    void inconsciente_deveSerVermelho() {
        var p = payload("Queda", 5, NivelConsciencia.INCONSCIENTE);
        assertEquals(CorManchester.VERMELHO, engine.classificar(p));
    }

    @Test
    @DisplayName("Dor no peito com irradiação para o braço → VERMELHO")
    void dorPeitoComIrradiacao_deveSerVermelho() {
        var p = payload("dor no peito irradiando para o braço esquerdo", 9, NivelConsciencia.ALERTA);
        assertEquals(CorManchester.VERMELHO, engine.classificar(p));
    }

    @Test
    @DisplayName("SatO2 abaixo de 85% → VERMELHO")
    void satBaixaCritica_deveSerVermelho() {
        var p = payload("Falta de ar", 6, NivelConsciencia.ALERTA);
        p.getSinaisVitais().setSaturacaoOxigenio(82);
        assertEquals(CorManchester.VERMELHO, engine.classificar(p));
    }

    @Test
    @DisplayName("Confusão mental → LARANJA")
    void confusao_deveSerLaranja() {
        var p = payload("Desorientação", 3, NivelConsciencia.CONFUSO);
        assertEquals(CorManchester.LARANJA, engine.classificar(p));
    }

    @Test
    @DisplayName("FC > 130 → LARANJA")
    void taquicardiaGrave_deveSerLaranja() {
        var p = payload("Palpitação", 5, NivelConsciencia.ALERTA);
        p.getSinaisVitais().setFrequenciaCardiaca(140);
        assertEquals(CorManchester.LARANJA, engine.classificar(p));
    }

    @Test
    @DisplayName("Dor moderada (6/10) → AMARELO")
    void dorModerada_deveSerAmarelo() {
        var p = payload("Dor nas costas", 6, NivelConsciencia.ALERTA);
        assertEquals(CorManchester.AMARELO, engine.classificar(p));
    }

    @Test
    @DisplayName("Febre 40°C → AMARELO")
    void febreAlta_deveSerAmarelo() {
        var p = payload("Febre", 2, NivelConsciencia.ALERTA);
        p.getSinaisVitais().setTemperatura(40.0);
        assertEquals(CorManchester.AMARELO, engine.classificar(p));
    }

    @Test
    @DisplayName("Dor leve (2/10) → VERDE")
    void dorLeve_deveSerVerde() {
        var p = payload("Dor de cabeça leve", 2, NivelConsciencia.ALERTA);
        assertEquals(CorManchester.VERDE, engine.classificar(p));
    }

    @Test
    @DisplayName("Sem queixa grave → AZUL")
    void semQueixaGrave_deveSerAzul() {
        var p = payload("Renovação de receita", 0, NivelConsciencia.ALERTA);
        assertEquals(CorManchester.AZUL, engine.classificar(p));
    }

    @Test
    @DisplayName("Gestante com PA > 160 → LARANJA")
    void gestanteComHipertensao_deveSerLaranja() {
        var p = payload("Dor de cabeça", 4, NivelConsciencia.ALERTA);
        p.setGestante(true);
        p.getSinaisVitais().setPressaoSistolica(170);
        assertEquals(CorManchester.LARANJA, engine.classificar(p));
    }

    // ---- helpers ----

    private TriagemPayloadDTO payload(String queixa, int dor, NivelConsciencia consciencia) {
        var sv = new SinaisVitaisDTO();
        sv.setPressaoSistolica(120);
        sv.setPressaoDiastolica(80);
        sv.setFrequenciaCardiaca(80);
        sv.setTemperatura(37.0);
        sv.setSaturacaoOxigenio(98);
        sv.setFrequenciaRespiratoria(16);

        var p = new TriagemPayloadDTO();
        p.setTriagemId("test-id");
        p.setHospitalId("hosp-test");
        p.setNome("Paciente Teste");
        p.setIdade(40);
        p.setQueixaPrincipal(queixa);
        p.setSinaisVitais(sv);
        p.setIntensidadeDor(dor);
        p.setNivelConsciencia(consciencia);
        p.setInicioSubito(false);
        return p;
    }
}
